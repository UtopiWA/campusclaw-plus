package com.campusclaw.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "grading_outbox")
public class GradingOutbox {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private GradingEventType eventType;

    @Column(name = "task_id", nullable = false, length = 36)
    private String taskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GradingOutboxStatus status;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error_code", length = 40)
    private String lastErrorCode;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GradingOutbox() {
    }

    public GradingOutbox(GradingEventType eventType, String taskId, Instant availableAt) {
        this.eventType = eventType;
        this.taskId = taskId;
        this.status = GradingOutboxStatus.PENDING;
        this.availableAt = availableAt;
        this.attempts = 0;
        this.createdAt = Instant.now();
    }

    public void markPublished() {
        status = GradingOutboxStatus.PUBLISHED;
        publishedAt = Instant.now();
        lastErrorCode = null;
    }

    public void scheduleRetry(Instant nextAttempt, String code) {
        status = GradingOutboxStatus.PENDING;
        availableAt = nextAttempt;
        lastErrorCode = code;
        attempts++;
    }

    public Long getId() { return id; }
    public GradingEventType getEventType() { return eventType; }
    public String getTaskId() { return taskId; }
    public GradingOutboxStatus getStatus() { return status; }
    public Instant getAvailableAt() { return availableAt; }
    public int getAttempts() { return attempts; }
    public String getLastErrorCode() { return lastErrorCode; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
