package com.campusclaw.health;

import static org.assertj.core.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class QdrantHealthProbeTest {
    @Test void springSelectsProductionConstructor() {
        var properties = org.mockito.Mockito.mock(com.campusclaw.config.AppProperties.class);
        org.mockito.Mockito.when(properties.qdrant()).thenReturn(
                new com.campusclaw.config.AppProperties.Qdrant("http://127.0.0.1:6333", "unused"));
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withBean(com.campusclaw.config.AppProperties.class, () -> properties)
                .withUserConfiguration(QdrantHealthProbe.class)
                .run(context -> assertThat(context).hasSingleBean(QdrantHealthProbe.class));
    }

    @Test void onlyGetsReadinessAndDoesNotFollowRedirectsOrRetry() throws Exception {
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/readyz", exchange -> {
            calls.incrementAndGet();
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            exchange.getResponseHeaders().add("Location", "/write");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        try {
            var probe = new QdrantHealthProbe(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/readyz"));
            assertThat(probe.isReady()).isFalse();
            assertThat(calls.get()).isEqualTo(1);
        } finally { server.stop(0); }
    }

    @Test void boundsUnresponsiveDependency() throws Exception {
        var server = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
        try (server) {
            var probe = new QdrantHealthProbe(URI.create("http://127.0.0.1:" + server.getLocalPort() + "/readyz"));
            assertThat(org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(8), probe::isReady)).isFalse();
        }
    }
}
