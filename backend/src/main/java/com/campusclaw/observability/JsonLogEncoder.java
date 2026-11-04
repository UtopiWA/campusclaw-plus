package com.campusclaw.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Set;

/** 只输出固定字段，避免第三方异常和业务消息泄漏请求内容或凭据。 */
public class JsonLogEncoder extends EncoderBase<ILoggingEvent> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String GRADING_LOGGER = "com.campusclaw.grading.GradingLog";
    private static final Set<String> FIELDS = Set.of(
            "method", "path", "status", "durationMs", "failureCode", "taskId", "attempts");

    @Override public byte[] headerBytes() { return new byte[0]; }
    @Override public byte[] footerBytes() { return new byte[0]; }

    @Override
    public byte[] encode(ILoggingEvent event) {
        var row = new LinkedHashMap<String, Object>();
        row.put("timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
        row.put("level", event.getLevel().toString());
        row.put("logger", event.getLoggerName());
        row.put("event", "application_log");
        String id = event.getMDCPropertyMap().get("requestId");
        row.put("requestId", id != null && id.matches("[A-Za-z0-9_-]{1,64}") ? id : null);
        String logger = event.getLoggerName();
        if ((RequestLoggingFilter.class.getName().equals(logger) || GRADING_LOGGER.equals(logger))
                && event.getKeyValuePairs() != null) {
            for (var pair : event.getKeyValuePairs()) {
                if ("event".equals(pair.key) && pair.value != null) {
                    row.put("event", pair.value);
                } else if (FIELDS.contains(pair.key)) {
                    row.put(pair.key, pair.value);
                }
            }
        }
        if (event.getThrowableProxy() != null) row.put("exceptionType", event.getThrowableProxy().getClassName());
        try {
            return (JSON.writeValueAsString(row) + "\n").getBytes(StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return "{\"event\":\"log_encoding_failed\"}\n".getBytes(StandardCharsets.UTF_8);
        }
    }
}
