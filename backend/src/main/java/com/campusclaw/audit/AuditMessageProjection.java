package com.campusclaw.audit;

import com.campusclaw.persistence.MessageStatus;
import java.time.Instant;

/** 成功技能轮次的只读投影，不加载消息正文。 */
public interface AuditMessageProjection {
    Long getId();
    Long getConversationId();
    Long getUserId();
    Long getSkillId();
    String getSkillName();
    MessageStatus getStatus();
    Integer getPromptTokens();
    Integer getCompletionTokens();
    Integer getTotalTokens();
    Long getModelDurationMs();
    String getRequestId();
    Instant getCreatedAt();
}
