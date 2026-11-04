package com.campusclaw.observability;

import static org.assertj.core.api.Assertions.*;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.AsyncEvent;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class RequestLoggingFilterTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final ListAppender<ILoggingEvent> events = new ListAppender<>() {
        @Override protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };
    @BeforeEach void start() { events.start(); logger.addAppender(events); }
    @AfterEach void stop() { logger.detachAppender(events); MDC.clear(); }

    @Test void validatesHeadersAndKeepsStatusesAndRestoresMdc() throws Exception {
        for (String input : new String[]{"a", "a".repeat(64), "a".repeat(65), "bad/value", "", "two"}) {
            var request = new MockHttpServletRequest("GET", "/api/materials?secret=PRIVATE-MARKER");
            request.addHeader("X-Request-Id", input);
            if (input.equals("two")) request.addHeader("X-Request-Id", "another");
            var response = new MockHttpServletResponse();
            MDC.put("requestId", "previous");
            new RequestLoggingFilter().doFilter(request, response, (req, res) -> response.setStatus(401));
            String id = response.getHeader("X-Request-Id");
            assertThat(id).matches("[A-Za-z0-9_-]{1,64}");
            if (input.length() == 1 || input.length() == 64) assertThat(id).isEqualTo(input);
            else assertThat(id).isNotEqualTo(input);
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(MDC.get("requestId")).isEqualTo("previous");
        }
        assertThat(events.list).hasSize(6);
        assertThat(events.list).allSatisfy(event -> {
            assertThat(event.getKeyValuePairs().toString()).doesNotContain("PRIVATE-MARKER");
            assertThat(event.getMDCPropertyMap().get("requestId")).isNotEqualTo("previous");
        });
        for (int status : new int[]{403, 404, 200}) {
            var response = new MockHttpServletResponse();
            new RequestLoggingFilter().doFilter(new MockHttpServletRequest("GET", "/unknown"), response,
                    (req, res) -> response.setStatus(status));
            assertThat(response.getHeader("X-Request-Id")).isNotBlank();
            assertThat(response.getStatus()).isEqualTo(status);
        }
    }

    @Test void asyncCompletesOnceAfterErrorTimeoutAndDuplicateCompletion() throws Exception {
        for (String outcome : new String[]{"success", "error", "timeout"}) {
            events.list.clear();
            var request = new MockHttpServletRequest("GET", "/api/conversations/1/messages");
            request.setAsyncSupported(true);
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/conversations/{id}/messages");
            var response = new MockHttpServletResponse();
            new RequestLoggingFilter().doFilter(request, response, (req, res) -> request.startAsync(req, res));
            assertThat(events.list).isEmpty();
            var context = (org.springframework.mock.web.MockAsyncContext) request.getAsyncContext();
            request.setDispatcherType(jakarta.servlet.DispatcherType.ASYNC);
            request.setAsyncStarted(false);
            new RequestLoggingFilter().doFilter(request, response, (req, res) -> { });
            assertThat(events.list).isEmpty();
            var event = new AsyncEvent(context, request, response, new java.io.IOException("client disconnected"));
            for (var listener : context.getListeners()) {
                if (outcome.equals("error")) listener.onError(event);
                if (outcome.equals("timeout")) listener.onTimeout(event);
                listener.onComplete(event);
                listener.onComplete(event);
            }
            assertThat(events.list).hasSize(1);
            assertThat(MDC.get("requestId")).isNull();
        }
    }

    @Test void unhandledFailureProducesOneCompletionWithFailureStatus() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/probe");
        var response = new MockHttpServletResponse();
        assertThatThrownBy(() -> new RequestLoggingFilter().doFilter(request, response, (req, res) -> {
            throw new jakarta.servlet.ServletException("PRIVATE-MARKER");
        })).isInstanceOf(jakarta.servlet.ServletException.class);
        assertThat(events.list).hasSize(1);
        var encoded = new String(new JsonLogEncoder().encode(events.list.get(0)), java.nio.charset.StandardCharsets.UTF_8);
        var row = new tools.jackson.databind.ObjectMapper().readTree(encoded);
        assertThat(row.path("status").asInt()).isEqualTo(500);
        assertThat(row.path("durationMs").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(encoded).doesNotContain("PRIVATE-MARKER");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test void workerReuseRestoresPreviousContextEvenOnFailure() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> MDC.setContextMap(Map.of("requestId", "worker"))).get();
            for (String id : new String[]{"first", "second"}) {
                MDC.put("requestId", id);
                Runnable task = new MdcTaskDecorator().decorate(() -> {
                    assertThat(MDC.get("requestId")).isEqualTo(id);
                    MDC.put("requestId", "changed");
                    throw new IllegalStateException("controlled");
                });
                assertThatThrownBy(() -> executor.submit(task).get()).hasCauseInstanceOf(IllegalStateException.class);
                assertThat(executor.submit(() -> MDC.get("requestId")).get()).isEqualTo("worker");
            }
        } finally { executor.shutdownNow(); }
    }
}
