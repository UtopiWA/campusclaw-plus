package com.campusclaw.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Set;

/** 只输出固定字段，避免第三方异常和业务消息泄漏请求内容或凭据。 */
public class JsonLogEncoder extends EncoderBase<ILoggingEvent> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> FIELDS = Set.of("method", "path", "status", "durationMs", "failureCode");

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
        if (RequestLoggingFilter.class.getName().equals(event.getLoggerName()) && event.getKeyValuePairs() != null) {
            for (var pair : event.getKeyValuePairs()) {
                if ("event".equals(pair.key) && "http_request_completed".equals(pair.value)) {
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
