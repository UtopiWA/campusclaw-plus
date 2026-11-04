package com.campusclaw.gateway;

import com.campusclaw.common.DependencyRejectedException;
import com.campusclaw.config.AppProperties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class GatewayRestClientFactoryTest {
    @Test
    void invalidCredentialsAreNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<String> operation = () -> {
            calls.incrementAndGet();
            throw new HttpClientErrorException(HttpStatus.UNAUTHORIZED);
        };
        assertThrows(DependencyRejectedException.class,
                () -> new GatewayRestClientFactory(mock(AppProperties.class)).withClassifiedRetry("Chat service", operation));
        assertEquals(1, calls.get());
    }

    @Test
    void rateLimitCanStillRecoverOnRetry() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<String> operation = () -> {
            if (calls.incrementAndGet() == 1) throw new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS);
            return "ok";
        };
        assertEquals("ok", new GatewayRestClientFactory(mock(AppProperties.class)).withClassifiedRetry("Chat service", operation));
        assertEquals(2, calls.get());
    }
}
