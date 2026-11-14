package com.campusclaw.persistence;

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

/** 保存可恢复的消息正文，以及当轮技能和引用展示快照。 */
@Entity
@Table(name = "messages")
public class ConversationMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "skill_name", length = 255)
    private String skillName;

    @Column(name = "skill_id")
    private Long skillId;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "model_duration_ms")
    private Long modelDurationMs;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "citations_json", nullable = false, columnDefinition = "JSON")
    private String citationsJson;

    @Column(name = "tool_calls_json", columnDefinition = "JSON")
    private String toolCallsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ConversationMessage() {
    }

    public ConversationMessage(Long conversationId, MessageRole role, String content,
                               String skillName, String citationsJson, MessageStatus status) {
        this(conversationId, role, content, skillName, citationsJson, null, status);
    }

    public ConversationMessage(Long conversationId, MessageRole role, String content,
                               String skillName, String citationsJson, String toolCallsJson,
                               MessageStatus status) {
        this(conversationId, role, content, skillName, null, null, null, null, null, null,
                citationsJson, toolCallsJson, status);
    }

    public ConversationMessage(Long conversationId, MessageRole role, String content,
                               String skillName, Long skillId, Integer promptTokens,
                               Integer completionTokens, Integer totalTokens,
                               Long modelDurationMs, String requestId,
                               String citationsJson, String toolCallsJson,
                               MessageStatus status) {
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.skillName = skillName;
        this.skillId = skillId;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.modelDurationMs = modelDurationMs;
        this.requestId = requestId;
        this.citationsJson = citationsJson;
        this.toolCallsJson = toolCallsJson;
        this.status = status;
    }

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public MessageRole getRole() { return role; }
    public String getContent() { return content; }
    public String getSkillName() { return skillName; }
    public Long getSkillId() { return skillId; }
    public Integer getPromptTokens() { return promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
    public Integer getTotalTokens() { return totalTokens; }
    public Long getModelDurationMs() { return modelDurationMs; }
    public String getRequestId() { return requestId; }
    public String getCitationsJson() { return citationsJson; }
    public String getToolCallsJson() { return toolCallsJson; }
    public MessageStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
