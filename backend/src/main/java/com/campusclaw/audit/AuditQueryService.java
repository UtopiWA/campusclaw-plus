package com.campusclaw.audit;

import com.campusclaw.common.BadRequestException;
import com.campusclaw.persistence.MessageRole;
import com.campusclaw.persistence.MessageStatus;
import com.campusclaw.persistence.Role;
import com.campusclaw.persistence.UserAccount;
import com.campusclaw.persistence.UserAccountRepository;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 合并成功消息与拒绝/失败事件，按班级和可选操作人/技能/时间窗回查。 */
@Service
public class AuditQueryService {
    private static final int MAX_RANGE_DAYS = 366;

    private final SecurityAuditEventRepository events;
    private final UserAccountRepository users;

    public AuditQueryService(SecurityAuditEventRepository events, UserAccountRepository users) {
        this.events = events;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<AuditEventView> query(UserAccount teacher, Long userId, Long skillId,
                                      String fromText, String toText, int limit) {
        if (teacher.getRole() != Role.TEACHER) {
            throw new AccessDeniedException("Teacher role required");
        }
        if (limit < 1 || limit > 100) {
            throw new BadRequestException("limit 必须在 1 到 100 之间");
        }
        Window window = window(fromText, toText);
        PageRequest page = PageRequest.of(0, limit);
        List<AuditMessageProjection> messageRows = events.findAuditMessages(
                teacher.getClassId(), MessageRole.ASSISTANT, userId, skillId,
                window.from(), window.to(), page);
        List<SecurityAuditEvent> eventRows = events.findAuditEvents(
                teacher.getClassId(), userId, skillId, window.from(), window.to(), page);
        Map<Long, String> names = users.findAllById(Stream.concat(
                        messageRows.stream().map(AuditMessageProjection::getUserId),
                        eventRows.stream().map(SecurityAuditEvent::getUserId))
                        .filter(java.util.Objects::nonNull)
                        .distinct().toList()).stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getUsername));
        List<AuditEventView> merged = new ArrayList<>(messageRows.size() + eventRows.size());
        messageRows.forEach(row -> merged.add(fromMessage(row, names)));
        eventRows.forEach(row -> merged.add(fromEvent(row, names)));
        merged.sort(COMPARATOR);
        return merged.size() <= limit ? List.copyOf(merged) : List.copyOf(merged.subList(0, limit));
    }

    private AuditEventView fromMessage(AuditMessageProjection row, Map<Long, String> names) {
        boolean failed = row.getStatus() == MessageStatus.FAILED;
        return new AuditEventView("MESSAGE", row.getId(), row.getUserId(),
                name(row.getUserId(), names), AuditAction.SKILL_EXECUTE.name(),
                AuditObjectType.MESSAGE.name(), row.getId(), row.getSkillId(), row.getSkillName(),
                failed ? AuditStatus.FAILED.name() : AuditStatus.SUCCESS.name(), null,
                failed ? "MODEL_FAILED" : null, row.getPromptTokens(), row.getCompletionTokens(),
                row.getTotalTokens(), row.getModelDurationMs(), row.getRequestId(), row.getCreatedAt());
    }

    private AuditEventView fromEvent(SecurityAuditEvent row, Map<Long, String> names) {
        return new AuditEventView("EVENT", row.getId(), row.getUserId(), name(row.getUserId(), names),
                row.getAction().name(), row.getObjectType().name(), row.getObjectId(),
                row.getSkillId(), row.getSkillName(), row.getStatus().name(), row.getHttpStatus(),
                row.getFailureCode(), row.getPromptTokens(), row.getCompletionTokens(),
                row.getTotalTokens(), row.getDurationMs(), row.getRequestId(), row.getCreatedAt());
    }

    private String name(Long userId, Map<Long, String> names) {
        if (userId == null) return "anonymous";
        return names.getOrDefault(userId, "未知用户");
    }

    private Window window(String fromText, String toText) {
        if (fromText == null && toText == null) {
            Instant to = Instant.now();
            return new Window(to.minus(24, ChronoUnit.HOURS), to);
        }
        try {
            LocalDate end = toText == null ? LocalDate.now(ZoneOffset.UTC) : LocalDate.parse(toText);
            LocalDate start = fromText == null ? end.minusDays(1) : LocalDate.parse(fromText);
            if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) >= MAX_RANGE_DAYS) {
                throw new BadRequestException("请选择不超过366天的有效审计时间范围");
            }
            return new Window(start.atStartOfDay().toInstant(ZoneOffset.UTC),
                    end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        } catch (DateTimeException exception) {
            throw new BadRequestException("请输入有效日期，例如 2026-11-11");
        }
    }

    private static final Comparator<AuditEventView> COMPARATOR = (left, right) -> {
        int byTime = right.createdAt().compareTo(left.createdAt());
        if (byTime != 0) return byTime;
        int bySource = left.source().compareTo(right.source());
        if (bySource != 0) return bySource;
        return Long.compare(right.id(), left.id());
    };

    private record Window(Instant from, Instant to) {
    }
}
