package com.campusclaw.audit;

import java.time.Instant;

/** 审计查询只暴露安全元数据；不包含消息正文、提示词、引用或工具原始内容。 */
public record AuditEventView(
        String source,
        Long id,
        Long userId,
        String userName,
        String action,
        String objectType,
        Long objectId,
        Long skillId,
        String skillName,
        String status,
        Integer httpStatus,
        String failureCode,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Long durationMs,
        String requestId,
        Instant createdAt) {
}
