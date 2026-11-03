package com.campusclaw.grading;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

/** 评分依据以 JSON 保存；读取时严格反序列化，避免字段漂移。 */
@Component
public class RubricCodec {
    private final ObjectMapper mapper;

    public RubricCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<RubricCriterion> parse(String json) {
        try {
            return Arrays.asList(mapper.readValue(json, RubricCriterion[].class));
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid rubric", exception);
        }
    }

    public String write(List<RubricCriterion> criteria) {
        try {
            return mapper.writeValueAsString(criteria);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid rubric", exception);
        }
    }
}
