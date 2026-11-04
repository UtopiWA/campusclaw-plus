package com.campusclaw.grading;

import com.campusclaw.config.GradingProperties;
import com.campusclaw.persistence.Assignment;
import com.campusclaw.persistence.AssignmentRepository;
import com.campusclaw.persistence.GradingTask;
import com.campusclaw.persistence.GradingTaskRepository;
import com.campusclaw.persistence.Submission;
import com.campusclaw.persistence.SubmissionRepository;
import tools.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.time.Instant;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** 独立 worker 的消费者；认领、模型调用、结果落库和 ACK 分离。 */
@Component
@ConditionalOnProperty(prefix = "app.grading", name = "worker-enabled", havingValue = "true")
public class GradingWorker {
    private final GradingTaskRepository tasks;
    private final SubmissionRepository submissions;
    private final AssignmentRepository assignments;
    private final GradingModelService model;
    private final GradingTaskCompletionService completion;
    private final GradingProperties properties;
    private final ObjectMapper objectMapper;

    public GradingWorker(GradingTaskRepository tasks, SubmissionRepository submissions,
                         AssignmentRepository assignments, GradingModelService model,
                         GradingTaskCompletionService completion, GradingProperties properties,
                         ObjectMapper objectMapper) {
        this.tasks = tasks;
        this.submissions = submissions;
        this.assignments = assignments;
        this.model = model;
        this.completion = completion;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${app.grading.task-queue}", ackMode = "MANUAL")
    public void handle(String payload, Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        String taskId;
        try {
            GradingMessage message = objectMapper.readValue(payload, GradingMessage.class);
            taskId = message.taskId();
        } catch (Exception exception) {
            GradingLog.event("grading_message_invalid", null, "FAILED", 0, 0, "MESSAGE_INVALID");
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        if (taskId == null || taskId.isBlank()) {
            GradingLog.event("grading_message_invalid", null, "FAILED", 0, 0, "MESSAGE_INVALID");
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        int claimed = tasks.claim(taskId, Instant.now());
        if (claimed == 0) {
            GradingLog.event("grading_task_skipped", taskId, "SKIPPED", 0, 0, null);
            channel.basicAck(deliveryTag, false);
            return;
        }
        GradingTask task = tasks.findById(taskId).orElse(null);
        if (task == null) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        long started = System.nanoTime();
        try {
            Submission submission = submissions.findById(task.getSubmissionId())
                    .orElseThrow(() -> new GradingNonRetryableException("SUBMISSION_MISSING"));
            Assignment assignment = assignments.findById(task.getAssignmentId())
                    .orElseThrow(() -> new GradingNonRetryableException("ASSIGNMENT_MISSING"));
            GradingOutcome outcome = model.grade(assignment, submission);
            boolean completed = completion.complete(taskId, task.getAttempts(), submission.getId(),
                    submission.getVersion(), outcome);
            GradingLog.event(completed ? "grading_task_completed" : "grading_task_skipped", taskId,
                    completed ? "SUCCEEDED" : "SKIPPED", task.getAttempts(), elapsedMillis(started), null);
        } catch (GradingRetryableException exception) {
            handleFailure(task, exception.code(), true, started);
        } catch (GradingNonRetryableException exception) {
            handleFailure(task, exception.code(), false, started);
        } catch (RuntimeException exception) {
            handleFailure(task, "GRADING_INTERNAL", true, started);
        }
        channel.basicAck(deliveryTag, false);
    }

    private void handleFailure(GradingTask task, String code, boolean retryable, long started) {
        if (!retryable || task.getAttempts() >= task.getMaxAttempts()) {
            if (completion.fail(task.getTaskId(), task.getAttempts(), code)) {
                GradingLog.event("grading_task_completed", task.getTaskId(), "FAILED",
                        task.getAttempts(), elapsedMillis(started), code);
            }
            return;
        }
        Instant next = GradingBackoff.next(properties, task.getAttempts());
        if (completion.scheduleRetry(task.getTaskId(), task.getAttempts(), code, next)) {
            GradingLog.event("grading_retry_scheduled", task.getTaskId(), "PENDING",
                    task.getAttempts(), elapsedMillis(started), code);
        }
    }

    private long elapsedMillis(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }
}
