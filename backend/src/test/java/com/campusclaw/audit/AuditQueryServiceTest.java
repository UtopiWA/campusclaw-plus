package com.campusclaw.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.campusclaw.common.BadRequestException;
import com.campusclaw.persistence.MessageRole;
import com.campusclaw.persistence.MessageStatus;
import com.campusclaw.persistence.Role;
import com.campusclaw.persistence.UserAccount;
import com.campusclaw.persistence.UserAccountRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class AuditQueryServiceTest {

    @Test
    void mergesMessagesAndEventsNewestFirstAndEnrichesOperatorName() throws Exception {
        SecurityAuditEventRepository events = mock(SecurityAuditEventRepository.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        AuditQueryService service = new AuditQueryService(events, users);
        UserAccount teacher = user(100L, "teacher-a", Role.TEACHER, 1L);
        UserAccount student = user(6L, "student-a1", Role.STUDENT, 1L);
        when(users.findAllById(any())).thenReturn(List.of(student));

        AuditMessageProjection message = mock(AuditMessageProjection.class);
        when(message.getId()).thenReturn(11L);
        when(message.getUserId()).thenReturn(6L);
        when(message.getSkillId()).thenReturn(12L);
        when(message.getSkillName()).thenReturn("计算");
        when(message.getStatus()).thenReturn(MessageStatus.COMPLETED);
        when(message.getPromptTokens()).thenReturn(1);
        when(message.getCompletionTokens()).thenReturn(2);
        when(message.getTotalTokens()).thenReturn(3);
        when(message.getModelDurationMs()).thenReturn(42L);
        when(message.getRequestId()).thenReturn("req-1");
        when(message.getCreatedAt()).thenReturn(Instant.parse("2026-11-14T10:00:00Z"));
        when(events.findAuditMessages(eq(1L), eq(MessageRole.ASSISTANT), isNull(), isNull(),
                any(), any(), any())).thenReturn(List.of(message));

        SecurityAuditEvent denied = new SecurityAuditEvent(1L, 6L, AuditAction.CONFIG_WRITE,
                AuditObjectType.SKILL, 12L, 12L, null, AuditStatus.DENIED, 403, "FORBIDDEN",
                null, null, null, null, "req-2");
        setCreatedAt(denied, Instant.parse("2026-11-14T11:00:00Z"));
        when(events.findAuditEvents(eq(1L), isNull(), isNull(), any(), any(), any()))
                .thenReturn(List.of(denied));

        List<AuditEventView> result = service.query(teacher, null, null, null, null, 50);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).source()).isEqualTo("EVENT");
        assertThat(result.get(0).action()).isEqualTo("CONFIG_WRITE");
        assertThat(result.get(0).status()).isEqualTo("DENIED");
        assertThat(result.get(0).httpStatus()).isEqualTo(403);
        assertThat(result.get(0).userName()).isEqualTo("student-a1");
        assertThat(result.get(1).source()).isEqualTo("MESSAGE");
        assertThat(result.get(1).action()).isEqualTo("SKILL_EXECUTE");
        assertThat(result.get(1).status()).isEqualTo("SUCCESS");
        assertThat(result.get(1).skillId()).isEqualTo(12L);
        assertThat(result.get(1).promptTokens()).isEqualTo(1);
        assertThat(result.get(1).durationMs()).isEqualTo(42L);
    }

    @Test
    void rejectsNonTeacherInvalidLimitAndInvalidWindow() throws Exception {
        SecurityAuditEventRepository events = mock(SecurityAuditEventRepository.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        AuditQueryService service = new AuditQueryService(events, users);
        UserAccount teacher = user(100L, "teacher-a", Role.TEACHER, 1L);
        UserAccount student = user(6L, "student-a1", Role.STUDENT, 1L);

        assertThatThrownBy(() -> service.query(student, null, null, null, null, 50))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.query(teacher, null, null, null, null, 0))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.query(teacher, null, null, null, null, 101))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.query(teacher, null, null, "2026-11-15", "2026-11-14", 50))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.query(teacher, null, null, "2024-01-01", "2026-11-14", 50))
                .isInstanceOf(BadRequestException.class);
    }

    private UserAccount user(Long id, String username, Role role, Long classId) throws Exception {
        UserAccount user = new UserAccount(username, "hash", role, classId);
        Field field = UserAccount.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, id);
        return user;
    }

    private void setCreatedAt(SecurityAuditEvent event, Instant createdAt) throws Exception {
        Field field = SecurityAuditEvent.class.getDeclaredField("createdAt");
        field.setAccessible(true);
        field.set(event, createdAt);
    }
}
