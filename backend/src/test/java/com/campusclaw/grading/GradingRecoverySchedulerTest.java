package com.campusclaw.grading;

import com.campusclaw.config.AppProperties;
import com.campusclaw.config.GradingProperties;
import com.campusclaw.persistence.GradingTaskRepository;
import com.campusclaw.persistence.GradingTask;
import com.campusclaw.persistence.GradingTaskStatus;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GradingRecoverySchedulerTest {
    @Test
    void recoveryMustAllowBothModelNetworkAttempts() {
        AppProperties app = mock(AppProperties.class);
        when(app.retrieval()).thenReturn(new AppProperties.Retrieval(
                Duration.ofSeconds(3), Duration.ofSeconds(30), 1000, 10, 20, 0.35, 0.55, 0.45, 60, false));
        GradingProperties properties = mock(GradingProperties.class);
        GradingTaskRepository tasks = mock(GradingTaskRepository.class);
        GradingTaskCompletionService completion = mock(GradingTaskCompletionService.class);
        when(properties.staleRunningTimeout()).thenReturn(Duration.ofSeconds(60));
        assertThrows(IllegalArgumentException.class,
                () -> new GradingRecoveryScheduler(tasks, completion, properties, app));
        when(properties.staleRunningTimeout()).thenReturn(Duration.ofSeconds(90));
        assertDoesNotThrow(() -> new GradingRecoveryScheduler(tasks, completion, properties, app));
    }

    @Test
    void lostRecoveryRaceDoesNotLogAStateChange() {
        AppProperties app = mock(AppProperties.class);
        when(app.retrieval()).thenReturn(new AppProperties.Retrieval(
                Duration.ofSeconds(3), Duration.ofSeconds(30), 1000, 10, 20, 0.35, 0.55, 0.45, 60, false));
        GradingProperties properties = mock(GradingProperties.class);
        when(properties.staleRunningTimeout()).thenReturn(Duration.ofSeconds(90));
        when(properties.outboxBatchSize()).thenReturn(20);
        GradingTaskRepository tasks = mock(GradingTaskRepository.class);
        GradingTask task = mock(GradingTask.class);
        when(task.getTaskId()).thenReturn("task");
        when(task.getAttempts()).thenReturn(5);
        when(task.getMaxAttempts()).thenReturn(5);
        when(tasks.findByStatusAndStartedAtBefore(eq(GradingTaskStatus.RUNNING), any(), any()))
                .thenReturn(List.of(task));
        GradingTaskCompletionService completion = mock(GradingTaskCompletionService.class);
        when(completion.fail("task", 5, "STALE_RUNNING")).thenReturn(false);
        try (var log = mockStatic(GradingLog.class)) {
            new GradingRecoveryScheduler(tasks, completion, properties, app).recoverStale();
            log.verifyNoInteractions();
        }
    }
}
