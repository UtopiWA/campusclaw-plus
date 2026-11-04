package com.campusclaw.grading;

import com.campusclaw.persistence.GradingOutboxRepository;
import com.campusclaw.persistence.GradingResultRepository;
import com.campusclaw.persistence.GradingTask;
import com.campusclaw.persistence.GradingTaskRepository;
import com.campusclaw.persistence.GradingTaskStatus;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GradingTaskCompletionServiceTest {
    @Test
    void recoveredAttemptCannotCompleteRetryOrFailNewOwner() {
        GradingTaskRepository tasks = mock(GradingTaskRepository.class);
        GradingResultRepository results = mock(GradingResultRepository.class);
        GradingOutboxRepository outbox = mock(GradingOutboxRepository.class);
        GradingTask task = new GradingTask("task", 1L, 1L, 1L, 2L, 5, "key");
        ReflectionTestUtils.setField(task, "status", GradingTaskStatus.RUNNING);
        ReflectionTestUtils.setField(task, "attempts", 2);
        when(tasks.lockById("task")).thenReturn(Optional.of(task));
        GradingTaskCompletionService service = new GradingTaskCompletionService(tasks, results, outbox);
        service.complete("task", 1, 1L, 1, new GradingOutcome(100, 100, "[]", "ok", "model", "v1"));
        service.scheduleRetry("task", 1, "OLD_FAILURE", Instant.now());
        service.fail("task", 1, "OLD_FAILURE");
        assertEquals(GradingTaskStatus.RUNNING, task.getStatus());
        assertEquals(2, task.getAttempts());
        verifyNoInteractions(results, outbox);
    }

    @Test
    void terminalResultCannotBeReplacedByLateFailure() {
        GradingTaskRepository tasks = mock(GradingTaskRepository.class);
        GradingResultRepository results = mock(GradingResultRepository.class);
        GradingOutboxRepository outbox = mock(GradingOutboxRepository.class);
        GradingTask task = new GradingTask("task", 1L, 1L, 1L, 2L, 5, "key");
        ReflectionTestUtils.setField(task, "status", GradingTaskStatus.SUCCEEDED);
        ReflectionTestUtils.setField(task, "attempts", 1);
        when(tasks.lockById("task")).thenReturn(Optional.of(task));
        new GradingTaskCompletionService(tasks, results, outbox).fail("task", 1, "LATE_FAILURE");
        assertEquals(GradingTaskStatus.SUCCEEDED, task.getStatus());
        verifyNoInteractions(results, outbox);
    }
}
