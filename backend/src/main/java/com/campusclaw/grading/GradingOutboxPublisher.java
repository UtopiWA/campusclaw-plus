package com.campusclaw.grading;

import com.campusclaw.config.GradingProperties;
import com.campusclaw.persistence.GradingEventType;
import com.campusclaw.persistence.GradingOutbox;
import com.campusclaw.persistence.GradingOutboxRepository;
import com.campusclaw.persistence.GradingOutboxStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 在数据库事务之外投递发件箱；失败保留记录并按固定延迟重试。 */
@Component
@ConditionalOnProperty(prefix = "app.grading", name = "publisher-enabled", havingValue = "true")
public class GradingOutboxPublisher {
    private final GradingOutboxRepository outbox;
    private final GradingProperties properties;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public GradingOutboxPublisher(GradingOutboxRepository outbox, GradingProperties properties,
                                  RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.properties = properties;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 1000)
    public void publishDue() {
        List<GradingOutbox> due = outbox.findDue(GradingOutboxStatus.PENDING, Instant.now(),
                PageRequest.of(0, properties.outboxBatchSize()));
        for (GradingOutbox event : due) {
            publish(event);
        }
    }

    private void publish(GradingOutbox event) {
        try {
            String payload = objectMapper.writeValueAsString(new GradingMessage(event.getTaskId()));
            CorrelationData correlation = new CorrelationData(String.valueOf(event.getId()));
            rabbitTemplate.convertAndSend(properties.exchange(), routingKey(event.getEventType()),
                    payload, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(properties.confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) {
                throw new IllegalStateException("publish not confirmed");
            }
            event.markPublished();
            outbox.save(event);
            GradingLog.event("grading_outbox_published", event.getTaskId(),
                    event.getEventType().name(), event.getAttempts(), 0, null);
        } catch (Exception exception) {
            event.scheduleRetry(Instant.now().plus(properties.outboxRetryDelay()), "BROKER_UNAVAILABLE");
            outbox.save(event);
            GradingLog.event("grading_outbox_publish_failed", event.getTaskId(),
                    event.getEventType().name(), event.getAttempts(), 0, "BROKER_UNAVAILABLE");
        }
    }

    private String routingKey(GradingEventType type) {
        return switch (type) {
            case GRADING_DISPATCH -> properties.dispatchRoutingKey();
            case GRADING_RETRY -> properties.retryRoutingKey();
            case GRADING_DEAD_LETTER -> properties.deadLetterRoutingKey();
        };
    }
}
