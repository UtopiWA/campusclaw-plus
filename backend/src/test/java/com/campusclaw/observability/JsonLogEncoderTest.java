package com.campusclaw.observability;

import static org.assertj.core.api.Assertions.*;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonLogEncoderTest {
    @Test void learningFailureLogsKeepOnlySafeEventFields() {
        var event = new LoggingEvent();
        event.setTimeStamp(1234);
        event.setLevel(Level.WARN);
        event.setLoggerName("com.campusclaw.learning.LearningPlanService");
        event.setMessage("PRIVATE-MODEL-BODY");
        event.setMDCPropertyMap(Map.of("requestId", "plan_test"));
        event.addKeyValuePair(new org.slf4j.event.KeyValuePair("event", "learning_plan_failed"));
        event.addKeyValuePair(new org.slf4j.event.KeyValuePair("failureCode", "MODEL_FIELDS_INVALID"));
        for (String key : new String[]{"goal", "response", "prompt", "token"}) {
            event.addKeyValuePair(new org.slf4j.event.KeyValuePair(key, "PRIVATE-MODEL-BODY"));
        }
        String encoded = new String(new JsonLogEncoder().encode(event), StandardCharsets.UTF_8);
        assertThat(encoded).contains("learning_plan_failed", "MODEL_FIELDS_INVALID", "plan_test").doesNotContain("PRIVATE-MODEL-BODY");
    }

    @Test void everyLineIsJsonAndRawMessagesArgumentsAndExceptionsAreExcluded() throws Exception {
        for (String message : new String[]{"startup", "中文\nsecond line", "Bearer PRIVATE-MARKER /home/private"}) {
            var event = new LoggingEvent();
            event.setTimeStamp(1234);
            event.setLevel(Level.ERROR);
            event.setLoggerName("com.campusclaw.Example");
            event.setMessage(message);
            event.setArgumentArray(new Object[]{"PRIVATE-MARKER"});
            event.addKeyValuePair(new org.slf4j.event.KeyValuePair("path", "PRIVATE-MARKER"));
            event.setThrowableProxy(new ThrowableProxy(new RuntimeException("PRIVATE-MARKER")));
            event.setMDCPropertyMap(Map.of("requestId", "abc_123", "password", "PRIVATE-MARKER"));
            String encoded = new String(new JsonLogEncoder().encode(event), StandardCharsets.UTF_8);
            assertThat(encoded.lines().count()).isEqualTo(1);
            assertThat(encoded).doesNotContain("PRIVATE-MARKER", "/home/private", "second line");
            var json = new ObjectMapper().readTree(encoded);
            assertThat(json.path("requestId").asText()).isEqualTo("abc_123");
            assertThat(json.path("event").asText()).isEqualTo("application_log");
            assertThat(json.has("timestamp") && json.has("level") && json.has("logger")).isTrue();
        }
    }
}
