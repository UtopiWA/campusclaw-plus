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
    public void complete(String taskId, Long submissionId, int version, GradingOutcome outcome) {
        GradingTask task = tasks.findById(taskId).orElseThrow();
        if (task.getStatus() != GradingTaskStatus.RUNNING) return;
        if (results.findBySubmissionIdAndVersion(submissionId, version).isEmpty()) {
            results.saveAndFlush(new GradingResult(taskId, submissionId, version, outcome.score(),
                    outcome.maxScore(), outcome.criteriaJson(), outcome.rationale(),
                    outcome.modelName(), outcome.promptVersion()));
        }
        task.markSucceeded();
        tasks.save(task);
    }

    /** 结果已由并发执行写入时，只补齐任务终态，不覆盖结果。 */
    @Transactional
    public void markSucceededAfterConflict(String taskId) {
        GradingTask task = tasks.findById(taskId).orElseThrow();
        if (task.getStatus() == GradingTaskStatus.SUCCEEDED) return;
        task.markSucceeded();
        tasks.save(task);
    }

    @Transactional
    public void scheduleRetry(String taskId, String code, Instant nextAttempt) {
        GradingTask task = tasks.findById(taskId).orElseThrow();
        if (task.getStatus() != GradingTaskStatus.RUNNING) return;
        task.scheduleRetry(nextAttempt, code);
        tasks.save(task);
        outbox.save(new GradingOutbox(GradingEventType.GRADING_RETRY, taskId, nextAttempt));
    }

    @Transactional
    public void fail(String taskId, String code) {
        GradingTask task = tasks.findById(taskId).orElseThrow();
        if (task.getStatus() == GradingTaskStatus.SUCCEEDED || task.getStatus() == GradingTaskStatus.FAILED) {
            return;
        }
        task.markFailed(code);
        tasks.save(task);
        outbox.save(new GradingOutbox(GradingEventType.GRADING_DEAD_LETTER, taskId, Instant.now()));
    }
}
