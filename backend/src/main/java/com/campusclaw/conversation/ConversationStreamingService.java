package com.campusclaw.conversation;

import com.campusclaw.audit.SecurityAuditService;
import com.campusclaw.gateway.ChatGateway;
import com.campusclaw.persistence.ConversationMessage;
import com.campusclaw.persistence.MessageStatus;
import tools.jackson.databind.ObjectMapper;
import java.io.OutputStream;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 执行已完成首帧前准备的流式输出，并按完成或中断状态持久化助手消息。 */
@Service
public class ConversationStreamingService {
    private static final Logger log = LoggerFactory.getLogger(ConversationStreamingService.class);

    private final ConversationService conversations;
    private final ObjectMapper objectMapper;
    private final ConversationCitationNormalizer citationNormalizer;
    private final SecurityAuditService audit;

    public ConversationStreamingService(ConversationService conversations, ObjectMapper objectMapper,
                                        ConversationCitationNormalizer citationNormalizer) {
        this(conversations, objectMapper, citationNormalizer, null);
    }

    @Autowired
    public ConversationStreamingService(ConversationService conversations, ObjectMapper objectMapper,
                                        ConversationCitationNormalizer citationNormalizer,
                                        SecurityAuditService audit) {
        this.conversations = conversations;
        this.objectMapper = objectMapper;
        this.citationNormalizer = citationNormalizer;
        this.audit = audit;
    }

    public void stream(AgentTurnPreparation preparation, OutputStream output) {
        SseEventWriter writer = new SseEventWriter(output, objectMapper);
        StringBuilder visibleText = new StringBuilder();
        boolean[] completedMessage = {false};
        boolean[] modelBacked = {false};
        long[] modelStarted = {0};
        ChatGateway.ChatStream[] streamRef = {null};
        String requestId = MDC.get("requestId");
        AgentTurnPlan plan = preparation.plan();
        CitationMarkerFilter markerFilter = plan.citations().isEmpty() ? new CitationMarkerFilter() : null;
        try (var upstream = preparation.stream()) {
            streamRef[0] = upstream;
            modelBacked[0] = upstream.modelBacked();
            if (modelBacked[0]) {
                modelStarted[0] = System.nanoTime();
            }
            writer.write("meta", new SseMetaEvent("ready", plan.skillName(), plan.citations(), plan.toolCalls()));
            upstream.consume(delta -> appendDelta(delta, markerFilter, visibleText, writer));
            if (markerFilter != null) {
                appendVisible(markerFilter.finish(), visibleText, writer);
            }
            if (visibleText.toString().isBlank()) {
                throw new IllegalStateException("Chat stream produced no visible text");
            }
            ChatGateway.Usage usage = modelBacked[0] ? upstream.usage() : null;
            Long duration = modelBacked[0] ? elapsedMillis(modelStarted[0]) : null;
            ConversationCitationNormalizer.NormalizedAnswer normalized = citationNormalizer.normalize(
                    visibleText.toString(), plan.citations());
            ConversationMessage saved = saveAssistant(preparation, normalized, MessageStatus.COMPLETED,
                    usage, duration, requestId);
            completedMessage[0] = true;
            writer.write("done", Map.of("messageId", saved.getId()));
        } catch (RuntimeException exception) {
            if (!completedMessage[0] && !visibleText.toString().isBlank()) {
                ChatGateway.Usage usage = streamRef[0] == null ? null : streamRef[0].usage();
                Long duration = modelBacked[0] ? elapsedMillis(modelStarted[0]) : null;
                persistFailed(preparation, visibleText.toString(), usage, duration, requestId);
            } else if (!completedMessage[0]) {
                recordFailure(preparation, "MODEL_STREAM_FAILED", requestId);
            }
            if (!completedMessage[0]) {
                try {
                    writer.write("error", Map.of(
                            "code", "STREAM_FAILED",
                            "message", "回答生成中断，请重试"));
                } catch (RuntimeException writeFailure) {
                    log.warn("Conversation stream {} disconnected before error event", preparation.conversationId());
                }
            } else {
                log.warn("Conversation stream {} disconnected after assistant message commit",
                        preparation.conversationId());
            }
        }
    }

    private Long elapsedMillis(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    private void appendDelta(String delta, CitationMarkerFilter filter,
                             StringBuilder visibleText, SseEventWriter writer) {
        String visible = filter == null ? delta : filter.accept(delta);
        appendVisible(visible, visibleText, writer);
    }

    private void appendVisible(String visible, StringBuilder fullText, SseEventWriter writer) {
        if (visible == null || visible.isEmpty()) {
            return;
        }
        fullText.append(visible);
        writer.write("delta", Map.of("text", visible));
    }

    private void persistFailed(AgentTurnPreparation preparation, String partialText,
                               ChatGateway.Usage usage, Long duration, String requestId) {
        try {
            ConversationCitationNormalizer.NormalizedAnswer normalized = citationNormalizer.normalize(
                    partialText, preparation.plan().citations());
            saveAssistant(preparation, normalized, MessageStatus.FAILED, usage, duration, requestId);
        } catch (RuntimeException persistenceFailure) {
            log.warn("Could not persist interrupted assistant message for conversation {}",
                    preparation.conversationId());
            recordFailure(preparation, "MESSAGE_PERSIST_FAILED", requestId);
        }
    }

    private void recordFailure(AgentTurnPreparation preparation, String failureCode, String requestId) {
        if (audit == null) return;
        AgentTurnPlan plan = preparation.plan();
        audit.recordSkillFailure(preparation.classId(), preparation.userId(),
                preparation.conversationId(), plan.skillId(), plan.skillName(), failureCode, requestId);
    }

    private ConversationMessage saveAssistant(
            AgentTurnPreparation preparation,
            ConversationCitationNormalizer.NormalizedAnswer normalized,
            MessageStatus status, ChatGateway.Usage usage, Long duration, String requestId) {
        AgentTurnPlan plan = preparation.plan();
        return conversations.saveAssistantMessage(
                preparation.conversationId(), preparation.userId(), preparation.classId(),
                normalized.content(), plan.skillName(), plan.skillId(), usage, duration, requestId,
                normalized.citations(), plan.toolCalls(), status);
    }
}
