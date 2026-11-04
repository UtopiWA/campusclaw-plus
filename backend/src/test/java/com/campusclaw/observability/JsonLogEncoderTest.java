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
