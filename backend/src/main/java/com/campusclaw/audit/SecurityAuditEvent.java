package com.campusclaw.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/** 追加式安全审计：只记录拒绝/失败等无法表示为助手消息的动作，不保存正文或凭据。 */
@Entity
@Table(name = "security_audit_events")
public class SecurityAuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "object_type", nullable = false, length = 32)
    private AuditObjectType objectType;

    @Column(name = "object_id")
    private Long objectId;

    @Column(name = "skill_id")
    private Long skillId;

    @Column(name = "skill_name", length = 255)
    private String skillName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AuditStatus status;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SecurityAuditEvent() {
    }

    public SecurityAuditEvent(Long classId, Long userId, AuditAction action, AuditObjectType objectType,
                              Long objectId, Long skillId, String skillName, AuditStatus status,
                              Integer httpStatus, String failureCode, Integer promptTokens,
                              Integer completionTokens, Integer totalTokens, Long durationMs,
                              String requestId) {
        this.classId = classId;
        this.userId = userId;
        this.action = action;
        this.objectType = objectType;
        this.objectId = objectId;
        this.skillId = skillId;
        this.skillName = skillName;
        this.status = status;
        this.httpStatus = httpStatus;
        this.failureCode = failureCode;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.durationMs = durationMs;
        this.requestId = requestId;
    }

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getClassId() { return classId; }
    public Long getUserId() { return userId; }
    public AuditAction getAction() { return action; }
    public AuditObjectType getObjectType() { return objectType; }
    public Long getObjectId() { return objectId; }
    public Long getSkillId() { return skillId; }
    public String getSkillName() { return skillName; }
    public AuditStatus getStatus() { return status; }
    public Integer getHttpStatus() { return httpStatus; }
    public String getFailureCode() { return failureCode; }
    public Integer getPromptTokens() { return promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
    public Integer getTotalTokens() { return totalTokens; }
    public Long getDurationMs() { return durationMs; }
    public String getRequestId() { return requestId; }
    public Instant getCreatedAt() { return createdAt; }
}
