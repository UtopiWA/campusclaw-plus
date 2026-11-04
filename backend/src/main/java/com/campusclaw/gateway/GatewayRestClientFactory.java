package com.campusclaw.gateway;

import com.campusclaw.config.AppProperties;
import java.util.function.Supplier;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import com.campusclaw.common.DependencyRejectedException;

/** 统一外部请求超时与有限重试；异常消息不拼接请求地址、正文或响应正文。 */
@Component
public class GatewayRestClientFactory {
    private final AppProperties properties;

    public GatewayRestClientFactory(AppProperties properties) {
        this.properties = properties;
    }

    public RestClient create(String baseUrl, String apiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.retrieval().connectTimeout());
        requestFactory.setReadTimeout(properties.retrieval().readTimeout());
        RestClient.Builder builder = RestClient.builder().baseUrl(stripTrailingSlash(baseUrl))
                .requestFactory(requestFactory);
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + apiKey);
        }
        return builder.build();
    }

    public <T> T withOneRetry(String dependency, Supplier<T> action) {
        return execute(dependency, action, false);
    }

    /** 模型请求区分永久拒绝；其他网关保持已有重试约定。 */
    public <T> T withClassifiedRetry(String dependency, Supplier<T> action) {
        return execute(dependency, action, true);
    }

    private <T> T execute(String dependency, Supplier<T> action, boolean classifyClientErrors) {
        RestClientException firstFailure;
        try {
            return action.get();
        } catch (RestClientException exception) {
            if (classifyClientErrors) rejectPermanent(dependency, exception);
            firstFailure = exception;
        }
        try {
            return action.get();
        } catch (RestClientException exception) {
            if (classifyClientErrors) rejectPermanent(dependency, exception);
            firstFailure.addSuppressed(exception);
            throw new com.campusclaw.common.DependencyUnavailableException(dependency);
        }
    }

    private void rejectPermanent(String dependency, RestClientException exception) {
        if (exception instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            if (status >= 400 && status < 500 && status != 408 && status != 429) {
                throw new DependencyRejectedException(dependency);
            }
        }
    }

    public void withOneRetry(String dependency, Runnable action) {
        withOneRetry(dependency, () -> {
            action.run();
            return null;
        });
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
