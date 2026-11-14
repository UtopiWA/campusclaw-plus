package com.campusclaw.audit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campusclaw.auth.CurrentUserService;
import com.campusclaw.common.ApiExceptionHandler;
import com.campusclaw.persistence.UserAccount;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuditControllerTest {
    private AuditQueryService audit;
    private CurrentUserService currentUsers;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        audit = mock(AuditQueryService.class);
        currentUsers = mock(CurrentUserService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AuditController(audit, currentUsers))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void rejectsStudentsWithoutQueryingAudit() throws Exception {
        when(currentUsers.requireTeacher()).thenThrow(new AccessDeniedException("Teacher role required"));

        mvc.perform(get("/api/audit/events"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(audit);
    }

    @Test
    void returnsSafeAuditMetadataForTeacher() throws Exception {
        UserAccount teacher = mock(UserAccount.class);
        when(currentUsers.requireTeacher()).thenReturn(teacher);
        when(audit.query(eq(teacher), eq(6L), eq(12L), eq("2026-11-01"), eq("2026-11-14"), eq(20)))
                .thenReturn(List.of(new AuditEventView("MESSAGE", 11L, 6L, "student-a1",
                        "SKILL_EXECUTE", "MESSAGE", 11L, 12L, "计算", "SUCCESS", null, null,
                        1, 2, 3, 42L, "req-1", Instant.parse("2026-11-14T10:00:00Z"))));

        mvc.perform(get("/api/audit/events")
                        .param("userId", "6")
                        .param("skillId", "12")
                        .param("from", "2026-11-01")
                        .param("to", "2026-11-14")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source").value("MESSAGE"))
                .andExpect(jsonPath("$[0].skillId").value(12))
                .andExpect(jsonPath("$[0].promptTokens").value(1))
                .andExpect(jsonPath("$[0].content").doesNotExist());
    }
}
