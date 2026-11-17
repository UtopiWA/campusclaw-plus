package com.campusclaw.gateway;

import com.campusclaw.common.DependencyUnavailableException;
import com.campusclaw.config.AppProperties;
import tools.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** 调用 OpenAI-compatible embeddings API，并在边界校验数量和维度。 */
@Component
public class OpenAiEmbeddingGateway implements EmbeddingGateway {
    /**
     * 查询文本 → 向量的小型 LRU。向量只取决于文本和当前模型，
     * 不包含班级/用户数据；进程重启或配置变更后自然失效。
     * cacheSize=0 时关闭缓存，便于同脚本对比复测。
     */
    private final Map<String, List<Double>> cache;
    private final boolean cacheEnabled;
    private final Object cacheLock = new Object();
    private final RestClient client;
    private final GatewayRestClientFactory requests;
    private final AppProperties.Embedding properties;

    public OpenAiEmbeddingGateway(AppProperties appProperties, GatewayRestClientFactory requests) {
        this.properties = appProperties.embedding();
        this.requests = requests;
        this.client = requests.create(properties.baseUrl(), properties.apiKey());
        int limit = properties.cacheSize();
        this.cacheEnabled = limit > 0;
        this.cache = cacheEnabled ? new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<Double>> eldest) {
                return size() > limit;
            }
        } : Map.of();
    }

    @Override
    public List<List<Double>> embed(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        List<String> unique = List.copyOf(new LinkedHashSet<>(texts));
        Map<String, List<Double>> known = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        synchronized (cacheLock) {
            for (String text : unique) {
                List<Double> vector = cache.get(text);
                if (vector == null) {
                    missing.add(text);
                } else {
                    known.put(text, vector);
                }
            }
        }
        if (!missing.isEmpty()) {
            JsonNode response = requests.withOneRetry("Embedding service", () -> client.post()
                    .uri("/embeddings")
                    .body(Map.of("model", properties.model(), "input", missing))
                    .retrieve()
                    .body(JsonNode.class));
            if (response == null || !response.path("data").isArray()) {
                throw new DependencyUnavailableException("Embedding service");
            }
            List<JsonNode> rows = new ArrayList<>();
            response.path("data").forEach(rows::add);
            rows.sort(Comparator.comparingInt(row -> row.path("index").asInt()));
            if (rows.size() != missing.size()) {
                throw new DependencyUnavailableException("Embedding service");
            }
            for (int index = 0; index < rows.size(); index++) {
                JsonNode values = rows.get(index).path("embedding");
                if (!values.isArray() || values.size() != properties.dimension()) {
                    throw new DependencyUnavailableException("Embedding service");
                }
                List<Double> vector = new ArrayList<>(values.size());
                values.forEach(value -> vector.add(value.asDouble()));
                List<Double> frozen = List.copyOf(vector);
                known.put(missing.get(index), frozen);
                if (cacheEnabled) {
                    synchronized (cacheLock) {
                        cache.put(missing.get(index), frozen);
                    }
                }
            }
        }
        List<List<Double>> result = new ArrayList<>(texts.size());
        for (String text : texts) {
            result.add(known.get(text));
        }
        return List.copyOf(result);
    }
}
