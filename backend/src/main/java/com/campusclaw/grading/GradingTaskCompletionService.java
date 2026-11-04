package com.campusclaw.grading;

import com.campusclaw.persistence.GradingEventType;
import com.campusclaw.persistence.GradingOutbox;
import com.campusclaw.persistence.GradingOutboxRepository;
import com.campusclaw.persistence.GradingResult;
import com.campusclaw.persistence.GradingResultRepository;
import com.campusclaw.persistence.GradingTask;
import com.campusclaw.persistence.GradingTaskRepository;
import com.campusclaw.persistence.GradingTaskStatus;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GradingTaskCompletionService {
    private final GradingTaskRepository tasks;
    private final GradingResultRepository results;
    private final GradingOutboxRepository outbox;

    public GradingTaskCompletionService(GradingTaskRepository tasks, GradingResultRepository results,
                                        GradingOutboxRepository outbox) {
        this.tasks = tasks;
        this.results = results;
        this.outbox = outbox;
    }

    @Transactional
    public boolean complete(String taskId, int attempt, Long submissionId, int version, GradingOutcome outcome) {
        GradingTask task = tasks.lockById(taskId).orElseThrow();
        if (!ownsAttempt(task, attempt)) return false;
        if (results.findBySubmissionIdAndVersion(submissionId, version).isEmpty()) {
            results.saveAndFlush(new GradingResult(taskId, submissionId, version, outcome.score(),
                    outcome.maxScore(), outcome.criteriaJson(), outcome.rationale(),
                    outcome.modelName(), outcome.promptVersion()));
        }
        task.markSucceeded();
        tasks.save(task);
        return true;
    }

    @Transactional
    public boolean scheduleRetry(String taskId, int attempt, String code, Instant nextAttempt) {
        GradingTask task = tasks.lockById(taskId).orElseThrow();
        if (!ownsAttempt(task, attempt)) return false;
        task.scheduleRetry(nextAttempt, code);
        tasks.save(task);
        outbox.save(new GradingOutbox(GradingEventType.GRADING_RETRY, taskId, nextAttempt));
        return true;
    }

    @Transactional
    public boolean fail(String taskId, int attempt, String code) {
        GradingTask task = tasks.lockById(taskId).orElseThrow();
        if (!ownsAttempt(task, attempt)) return false;
        task.markFailed(code);
        tasks.save(task);
        outbox.save(new GradingOutbox(GradingEventType.GRADING_DEAD_LETTER, taskId, Instant.now()));
        return true;
    }

    /** 回收后旧执行可能迟到，只有当前认领的执行次数可以写回。 */
    private boolean ownsAttempt(GradingTask task, int attempt) {
        return task.getStatus() == GradingTaskStatus.RUNNING && task.getAttempts() == attempt;
    }
}
