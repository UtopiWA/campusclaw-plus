package com.campusclaw.grading;

import com.campusclaw.config.GradingProperties;
import com.campusclaw.persistence.GradingTask;
import com.campusclaw.persistence.GradingTaskRepository;
import com.campusclaw.persistence.GradingTaskStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 回收崩溃或超时留下的 RUNNING，避免任务永久卡住。 */
@Component
@ConditionalOnProperty(prefix = "app.grading", name = "recovery-enabled", havingValue = "true")
public class GradingRecoveryScheduler {
    private final GradingTaskRepository tasks;
    private final GradingTaskCompletionService completion;
    private final GradingProperties properties;

    public GradingRecoveryScheduler(GradingTaskRepository tasks, GradingTaskCompletionService completion,
                                    GradingProperties properties) {
        this.tasks = tasks;
        this.completion = completion;
        this.properties = properties;
    }

    @Scheduled(fixedDelay = 30000)
    public void recoverStale() {
        Instant cutoff = Instant.now().minus(properties.staleRunningTimeout());
        List<GradingTask> stale = tasks.findByStatusAndStartedAtBefore(GradingTaskStatus.RUNNING, cutoff);
        for (GradingTask task : stale) {
            if (task.getAttempts() >= task.getMaxAttempts()) {
                completion.fail(task.getTaskId(), "STALE_RUNNING");
                GradingLog.event("grading_task_recovered", task.getTaskId(), "FAILED",
                        task.getAttempts(), 0, "STALE_RUNNING");
            } else {
                completion.scheduleRetry(task.getTaskId(), "STALE_RUNNING",
                        GradingBackoff.next(properties, task.getAttempts()));
                GradingLog.event("grading_task_recovered", task.getTaskId(), "PENDING",
                        task.getAttempts(), 0, "STALE_RUNNING");
            }
        }
    }
}
