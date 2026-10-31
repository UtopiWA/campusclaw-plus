package com.campusclaw.health;

import com.campusclaw.config.AppProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class QdrantHealthProbe {
    private final URI endpoint;
    private final HttpClient client;

    @Autowired
    public QdrantHealthProbe(AppProperties properties) {
        this(URI.create(properties.qdrant().url().replaceAll("/+$", "") + "/readyz"));
    }

    QdrantHealthProbe(URI endpoint) {
        this.endpoint = endpoint;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public boolean isReady() {
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(1)).GET().build();
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return status >= 200 && status < 300;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception exception) {
            // 健康探测不重试、不回显远端地址或异常，也不执行集合管理。
            return false;
        }
    }
}
