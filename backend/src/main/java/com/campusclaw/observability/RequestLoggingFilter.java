package com.campusclaw.observability;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String STATE = RequestLoggingFilter.class.getName() + ".state";

    @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }
    @Override protected boolean shouldNotFilterErrorDispatch() { return false; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        State state = (State) request.getAttribute(STATE);
        if (state == null) {
            var headers = Collections.list(request.getHeaders("X-Request-Id"));
            String id = headers.size() == 1 && headers.get(0).matches("[A-Za-z0-9_-]{1,64}")
                    ? headers.get(0) : UUID.randomUUID().toString();
            state = new State(id, request, response);
            request.setAttribute(STATE, state);
        }
        response.setHeader("X-Request-Id", state.id);
        String previous = MDC.get("requestId");
        MDC.put("requestId", state.id);
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            state.failed = true;
            if (!response.isCommitted() && response.getStatus() < 400) state.unhandled = true;
            throw exception;
        } finally {
            try {
                if (request.isAsyncStarted()) {
                    state.async = true;
                    try { request.getAsyncContext().addListener(state); }
                    catch (IllegalStateException completed) { state.finish(); }
                } else if (!state.async) {
                    state.finish();
                }
            } finally {
                if (previous == null) {
                    MDC.remove("requestId");
                } else {
                    MDC.put("requestId", previous);
                }
            }
        }
    }

    private static final class State implements AsyncListener {
        final String id;
        final HttpServletRequest request;
        final HttpServletResponse response;
        final long started = System.nanoTime();
        final AtomicBoolean completed = new AtomicBoolean();
        volatile boolean failed;
        volatile boolean async;
        volatile boolean unhandled;

        State(String id, HttpServletRequest request, HttpServletResponse response) {
            this.id = id;
            this.request = request;
            this.response = response;
        }

        void finish() {
            if (!completed.compareAndSet(false, true)) return;
            String previous = MDC.get("requestId");
            MDC.put("requestId", id);
            try {
                Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                String path = pattern == null ? "/unmapped" : pattern.toString();
                if (!path.matches("[A-Za-z0-9_./{}*:-]{1,200}")) path = "/unmapped";
                String method = request.getMethod();
                if (method == null || !method.matches("[A-Z]{1,16}")) method = "OTHER";
                var event = LOG.atInfo().addKeyValue("event", "http_request_completed")
                        .addKeyValue("method", method).addKeyValue("path", path)
                        .addKeyValue("status", unhandled ? 500 : response.getStatus())
                        .addKeyValue("durationMs", Math.max(0, (System.nanoTime() - started) / 1_000_000));
                if (failed) event.addKeyValue("failureCode", "REQUEST_FAILED");
                event.log("http_request_completed");
            } finally {
                if (previous == null) {
                    MDC.remove("requestId");
                } else {
                    MDC.put("requestId", previous);
                }
            }
        }

        @Override public void onComplete(AsyncEvent event) { finish(); }
        @Override public void onTimeout(AsyncEvent event) { failed = true; }
        @Override public void onError(AsyncEvent event) { failed = true; }
        @Override public void onStartAsync(AsyncEvent event) { event.getAsyncContext().addListener(this); }
    }
}
