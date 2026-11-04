package com.campusclaw.grading;

import com.campusclaw.common.NotFoundException;
import com.campusclaw.persistence.AssignmentRepository;
import com.campusclaw.persistence.GradingResultRepository;
import com.campusclaw.persistence.GradingTask;
import com.campusclaw.persistence.GradingTaskRepository;
import com.campusclaw.persistence.Role;
import com.campusclaw.persistence.SubmissionRepository;
import com.campusclaw.persistence.UserAccount;
import com.campusclaw.persistence.UserAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GradingTaskQueryServiceTest {
    @Test
    void studentMovedToAnotherClassCannotReadOldClassTask() {
        GradingTaskRepository tasks = mock(GradingTaskRepository.class);
        GradingResultRepository results = mock(GradingResultRepository.class);
        when(tasks.findById("task")).thenReturn(Optional.of(
                new GradingTask("task", 1L, 1L, 1L, 2L, 5, "key")));
        UserAccount moved = new UserAccount("student", "synthetic", Role.STUDENT, 9L);
        ReflectionTestUtils.setField(moved, "id", 2L);
        GradingTaskQueryService service = new GradingTaskQueryService(tasks, results,
                mock(SubmissionRepository.class), mock(AssignmentRepository.class),
                mock(UserAccountRepository.class), new ObjectMapper());
        assertThrows(NotFoundException.class, () -> service.view("task", moved));
        verifyNoInteractions(results);
    }
}
