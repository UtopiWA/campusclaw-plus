package com.campusclaw.audit;

import com.campusclaw.persistence.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 写入无法表示为助手消息的拒绝/失败审计；单条保存失败不得掩盖原业务响应。 */
@Service
public class SecurityAuditService {
    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);

    private final SecurityAuditEventRepository events;
    private final TransactionTemplate requiresNew;

    public SecurityAuditService(SecurityAuditEventRepository events,
                                PlatformTransactionManager transactions) {
        this.events = events;
        this.requiresNew = new TransactionTemplate(transactions);
        // 拒绝/失败审计必须独立提交；调用方的事务随后回滚时不能把审计一起抹掉。
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void recordDenied(UserAccount user, AuditAction action, AuditObjectType objectType,
                             Long objectId, Long skillId, String skillName, int httpStatus,
                             String failureCode, String requestId) {
        record(user == null ? null : user.getClassId(), user == null ? null : user.getId(),
                action, objectType, objectId, skillId, skillName, AuditStatus.DENIED,
                httpStatus, failureCode, null, null, null, null, requestId);
    }

    public void recordSkillFailure(Long classId, Long userId, Long conversationId,
                                   Long skillId, String skillName, String failureCode,
                                   String requestId) {
        record(classId, userId, AuditAction.SKILL_EXECUTE, AuditObjectType.CONVERSATION,
                conversationId, skillId, skillName, AuditStatus.FAILED,
                null, failureCode, null, null, null, null, requestId);
    }

    public void record(Long classId, Long userId, AuditAction action, AuditObjectType objectType,
                       Long objectId, Long skillId, String skillName, AuditStatus status,
                       Integer httpStatus, String failureCode, Integer promptTokens,
                       Integer completionTokens, Integer totalTokens, Long durationMs,
                       String requestId) {
        if (classId == null) {
            return;
        }
        try {
            requiresNew.executeWithoutResult(ignored -> events.save(new SecurityAuditEvent(
                    classId, userId, action, objectType, objectId, skillId, skillName, status,
                    httpStatus, failureCode, promptTokens, completionTokens, totalTokens,
                    durationMs, requestId)));
        } catch (RuntimeException exception) {
            // 审计写入失败只记录稳定类别；不能改变原 401/403/404 或业务结果。
            log.warn("Security audit write failed for action {} status {}", action, status);
        }
    }
}
