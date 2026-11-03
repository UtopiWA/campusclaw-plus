package com.campusclaw.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "grading_tasks")
public class GradingTask {
    @Id
    @Column(name = "task_id", length = 36)
    private String taskId;

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GradingTaskStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "failure_code", length = 40)
    private String failureCode;

    @Column(name = "last_failure_code", length = 40)
    private String lastFailureCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GradingTask() {
    }

    public GradingTask(String taskId, Long submissionId, Long assignmentId, Long classId,
                       Long studentId, int maxAttempts, String idempotencyKey) {
        Instant now = Instant.now();
        this.taskId = taskId;
        this.submissionId = submissionId;
        this.assignmentId = assignmentId;
        this.classId = classId;
        this.studentId = studentId;
        this.status = GradingTaskStatus.PENDING;
        this.attempts = 0;
        this.maxAttempts = maxAttempts;
        this.idempotencyKey = idempotencyKey;
        this.availableAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markSucceeded() {
        Instant now = Instant.now();
        status = GradingTaskStatus.SUCCEEDED;
        finishedAt = now;
        updatedAt = now;
        failureCode = null;
    }

    public void markFailed(String code) {
        Instant now = Instant.now();
        status = GradingTaskStatus.FAILED;
        finishedAt = now;
        failureCode = code;
        updatedAt = now;
    }

    public void scheduleRetry(Instant nextAttempt, String code) {
        status = GradingTaskStatus.PENDING;
        availableAt = nextAttempt;
        startedAt = null;
        lastFailureCode = code;
        updatedAt = Instant.now();
    }

    public String getTaskId() { return taskId; }
    public Long getSubmissionId() { return submissionId; }
    public Long getAssignmentId() { return assignmentId; }
    public Long getClassId() { return classId; }
    public Long getStudentId() { return studentId; }
    public GradingTaskStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public int getMaxAttempts() { return maxAttempts; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getAvailableAt() { return availableAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getFailureCode() { return failureCode; }
    public String getLastFailureCode() { return lastFailureCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
