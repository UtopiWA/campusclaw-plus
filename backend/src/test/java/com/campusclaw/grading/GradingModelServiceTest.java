package com.campusclaw.grading;

import com.campusclaw.config.AppProperties;
import com.campusclaw.config.GradingProperties;
import com.campusclaw.gateway.ChatGateway;
import com.campusclaw.persistence.Assignment;
import com.campusclaw.persistence.Submission;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GradingModelServiceTest {

    @Test
    void computesDeterministicScoreAndIgnoresModelTotal() {
        ChatGateway chat = mock(ChatGateway.class);
        when(chat.complete(anyList())).thenReturn("""
                {"criteria":[
                  {"key":"c1","verdict":"MET","evidence":"a^2+b^2=c^2","explanation":"公式正确"},
                  {"key":"c2","verdict":"PARTIAL","evidence":"说明了斜边","explanation":"解释基本正确"}
                ],"overallRationale":"整体回答正确"}
                """);
        GradingOutcome outcome = service(chat).grade(assignment(), submission());

        assertEquals(80, outcome.score());
        assertEquals(100, outcome.maxScore());
        assertTrue(outcome.criteriaJson().contains("\"awardedPoints\":60"));
        assertTrue(outcome.criteriaJson().contains("\"awardedPoints\":20"));
        assertEquals("v1", outcome.promptVersion());
        assertEquals("course-chat", outcome.modelName());
    }

    @Test
    void rejectsUnknownCriterionAndModelTotal() {
        ChatGateway chat = mock(ChatGateway.class);
        when(chat.complete(anyList())).thenReturn("""
                {"criteria":[
                  {"key":"c1","verdict":"MET","evidence":"e","explanation":"x"},
                  {"key":"other","verdict":"MET","evidence":"e","explanation":"x"}
                ],"overallRationale":"x","score":80}
                """);
        GradingRetryableException exception = assertThrows(GradingRetryableException.class,
                () -> service(chat).grade(assignment(), submission()));
        assertEquals("MODEL_OUTPUT_INVALID", exception.code());
    }

    @Test
    void rejectsNonJsonAndMapsModelFailureToRetryable() {
        ChatGateway nonJson = mock(ChatGateway.class);
        when(nonJson.complete(anyList())).thenReturn("not json");
        assertEquals("MODEL_OUTPUT_INVALID", assertThrows(GradingRetryableException.class,
                () -> service(nonJson).grade(assignment(), submission())).code());

        ChatGateway failing = mock(ChatGateway.class);
        when(failing.complete(anyList())).thenThrow(new RuntimeException("provider down"));
        assertEquals("MODEL_UNAVAILABLE", assertThrows(GradingRetryableException.class,
                () -> service(failing).grade(assignment(), submission())).code());
    }

    @Test
    void acceptsSingleCodeFenceAroundJson() {
        ChatGateway chat = mock(ChatGateway.class);
        when(chat.complete(anyList())).thenReturn("""
                ```json
                {"criteria":[
                  {"key":"c1","verdict":"MET","evidence":"e","explanation":"x"},
                  {"key":"c2","verdict":"NOT_MET","evidence":"e","explanation":"x"}
                ],"overallRationale":"x"}
                ```
                """);
        GradingOutcome outcome = service(chat).grade(assignment(), submission());
        assertEquals(60, outcome.score());
    }

    @Test
    void rejectsMissingRationaleNullVerdictAndTrailingJson() {
        String valid = """
                {"criteria":[
                  {"key":"c1","verdict":"MET","evidence":"e","explanation":"x"},
                  {"key":"c2","verdict":"MET","evidence":"e","explanation":"x"}
                ],"overallRationale":"x"}
                """.trim();
        for (String invalid : List.of(valid.replace(",\"overallRationale\":\"x\"", ""),
                valid.replace("\"verdict\":\"MET\"", "\"verdict\":null"), valid + " {}")) {
            ChatGateway chat = mock(ChatGateway.class);
            when(chat.complete(anyList())).thenReturn(invalid);
            assertEquals("MODEL_OUTPUT_INVALID", assertThrows(GradingRetryableException.class,
                    () -> service(chat).grade(assignment(), submission())).code());
        }
    }

    private GradingModelService service(ChatGateway chat) {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        return new GradingModelService(chat, appProperties(), gradingProperties(),
                new RubricCodec(mapper), mapper);
    }

    private Assignment assignment() {
        String rubric = """
                [{"key":"c1","description":"公式正确","points":60},
                 {"key":"c2","description":"解释清楚","points":40}]
                """;
        return new Assignment(1L, "勾股定理", "解释勾股定理", rubric, 100, 1L);
    }

    private Submission submission() {
        return new Submission(1L, 1L, 2L,
                "在直角三角形中，a^2+b^2=c^2。", "hash", null, 1);
    }

    private AppProperties appProperties() {
        return new AppProperties(
                "uploads", 1000,
                new AppProperties.DemoSeed(false, ""),
                new AppProperties.Qdrant("http://localhost:6333", "chunks"),
                new AppProperties.Embedding("http://localhost", "key", "embedding", 1),
                new AppProperties.Chat("http://localhost", "key", "course-chat", "none"),
                new AppProperties.Retrieval(Duration.ofSeconds(1), Duration.ofSeconds(1),
                        1000, 10, 20, 0.35, 0.55, 0.45, 60, false));
    }

    private GradingProperties gradingProperties() {
        return new GradingProperties(false, false, false, 3,
                Duration.ofSeconds(1), 2, 0.0, Duration.ofMinutes(1),
                Duration.ofSeconds(90), Duration.ofSeconds(5), 10, Duration.ofSeconds(5),
                "v1", "grading.events", "grading.tasks", "grading.dlq",
                "grading.dispatch", "grading.retry", "grading.dead-letter");
    }
}
