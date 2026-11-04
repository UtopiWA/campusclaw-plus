package com.campusclaw.grading;

import com.campusclaw.config.GradingProperties;
import com.campusclaw.persistence.GradingEventType;
import com.campusclaw.persistence.GradingOutbox;
import com.campusclaw.persistence.GradingOutboxRepository;
import com.campusclaw.persistence.GradingOutboxStatus;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GradingOutboxPublisherTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unroutableAckCannotMarkOutboxPublished(boolean returned) {
        GradingProperties properties = mock(GradingProperties.class);
        when(properties.exchange()).thenReturn("events");
        when(properties.dispatchRoutingKey()).thenReturn("dispatch");
        when(properties.confirmTimeout()).thenReturn(Duration.ofSeconds(1));
        when(properties.outboxRetryDelay()).thenReturn(Duration.ofSeconds(1));
        when(properties.outboxBatchSize()).thenReturn(10);
        GradingOutboxRepository outbox = mock(GradingOutboxRepository.class);
        GradingOutbox event = new GradingOutbox(GradingEventType.GRADING_DISPATCH, "task", Instant.now());
        when(outbox.findDue(any(), any(), any())).thenReturn(List.of(event));
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            if (returned) correlation.setReturned(new ReturnedMessage(
                    new Message(new byte[0], new MessageProperties()), 312, "NO_ROUTE", "events", "dispatch"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).convertAndSend(eq("events"), eq("dispatch"), any(Object.class), any(CorrelationData.class));
        new GradingOutboxPublisher(outbox, properties, rabbit, new ObjectMapper()).publishDue();
        assertEquals(returned ? GradingOutboxStatus.PENDING : GradingOutboxStatus.PUBLISHED, event.getStatus());
    }
}
