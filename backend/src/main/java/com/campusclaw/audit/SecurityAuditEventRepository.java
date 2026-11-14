package com.campusclaw.audit;

import com.campusclaw.persistence.MessageRole;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SecurityAuditEventRepository extends JpaRepository<SecurityAuditEvent, Long> {

    @Query("""
            select e from SecurityAuditEvent e
            where e.classId = :classId
              and e.createdAt >= :from and e.createdAt < :to
              and (:userId is null or e.userId = :userId)
              and (:skillId is null or e.skillId = :skillId)
            order by e.createdAt desc, e.id desc
            """)
    List<SecurityAuditEvent> findAuditEvents(@Param("classId") Long classId,
                                             @Param("userId") Long userId,
                                             @Param("skillId") Long skillId,
                                             @Param("from") Instant from,
                                             @Param("to") Instant to,
                                             Pageable pageable);

    /** 单独放置以避免审计查询依赖消息正文；成功轮次只读取元数据列。 */
    @Query("""
            select m.id as id, m.conversationId as conversationId, c.userId as userId,
                   m.skillId as skillId, m.skillName as skillName, m.status as status,
                   m.promptTokens as promptTokens, m.completionTokens as completionTokens,
                   m.totalTokens as totalTokens, m.modelDurationMs as modelDurationMs,
                   m.requestId as requestId, m.createdAt as createdAt
            from ConversationMessage m, Conversation c
            where c.id = m.conversationId
              and c.classId = :classId
              and m.role = :role
              and m.skillId is not null
              and m.createdAt >= :from and m.createdAt < :to
              and (:userId is null or c.userId = :userId)
              and (:skillId is null or m.skillId = :skillId)
            order by m.createdAt desc, m.id desc
            """)
    List<AuditMessageProjection> findAuditMessages(@Param("classId") Long classId,
                                                   @Param("role") MessageRole role,
                                                   @Param("userId") Long userId,
                                                   @Param("skillId") Long skillId,
                                                   @Param("from") Instant from,
                                                   @Param("to") Instant to,
                                                   Pageable pageable);
}
