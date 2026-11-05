package com.campusclaw.grading;

import com.campusclaw.config.AppProperties;
import com.campusclaw.config.GradingProperties;
import com.campusclaw.common.DependencyRejectedException;
import com.campusclaw.gateway.ChatGateway;
import com.campusclaw.persistence.Assignment;
import com.campusclaw.persistence.Submission;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class GradingModelServiceTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 87, 100})
    void savesModelScoreWithoutFixedCriterionWeights(int score) {
        ChatGateway chat = mock(ChatGateway.class);
        when(chat.complete(anyList())).thenAnswer(invocation -> {
            List<ChatGateway.ChatMessage> messages = invocation.getArgument(0);
            String prompt = messages.get(1).content();
            assertTrue(prompt.contains("a²+b²=c²"));
            assertTrue(prompt.contains("总分上限（服务端设定）：100"));
            assertTrue(prompt.contains("<<<ANSWER"));
            assertFalse(prompt.contains("MET|PARTIAL|NOT_MET"));
            return "{\"score\":" + score + ",\"rationale\":\"公式正确，尚需补充推理及适用条件。\"}";
        });
        GradingOutcome outcome = service(chat).grade(assignment(), submission());
        assertEquals(score, outcome.score());
        assertEquals(100, outcome.maxScore());
        assertEquals("[]", outcome.criteriaJson());
        assertEquals("v4", outcome.promptVersion());
        assertEquals("course-chat", outcome.modelName());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "not json", "null", "{}", "{\"score\":87}",
        "{\"score\":-1,\"rationale\":\"依据\"}",
        "{\"score\":101,\"rationale\":\"依据\"}",
        "{\"score\":87.5,\"rationale\":\"依据\"}",
        "{\"score\":\"87\",\"rationale\":\"依据\"}",
        "{\"score\":null,\"rationale\":\"依据\"}",
        "{\"score\":2147483648,\"rationale\":\"依据\"}",
        "{\"score\":87,\"rationale\":42}",
        "{\"score\":87,\"rationale\":\" \"}",
        "{\"score\":87,\"rationale\":null}",
        "{\"score\":87,\"rationale\":\"依据\",\"criteria\":[]}",
        "{\"score\":87,\"rationale\":\"依据\"} {}"
    })
    void rejectsInvalidScoresOrStructureWithoutClampingOrDefaults(String response) {
        ChatGateway chat = mock(ChatGateway.class);
        when(chat.complete(anyList())).thenReturn(response);
        assertEquals("MODEL_OUTPUT_INVALID", assertThrows(GradingRetryableException.class,
                () -> service(chat).grade(assignment(), submission())).code());
    }

    @Test
    void rejectsLongRationaleAndAcceptsSingleFence() {
        ChatGateway chat = mock(ChatGateway.class);
        when(chat.complete(anyList())).thenReturn("{\"score\":87,\"rationale\":\"" + "学".repeat(2001) + "\"}");
        assertThrows(GradingRetryableException.class, () -> service(chat).grade(assignment(), submission()));
        when(chat.complete(anyList())).thenReturn("```json\n{\"score\":87,\"rationale\":\"证据与建议\"}\n```");
        assertEquals(87, service(chat).grade(assignment(), submission()).score());
    }

    @Test
    void rejectsMissingReferenceAndDoesNotCallModel() {
        ChatGateway chat = mock(ChatGateway.class);
        Assignment invalid = new Assignment(1L, "Title", "Question", "[]", 100, 1L);
        assertEquals("ASSIGNMENT_INVALID", assertThrows(GradingNonRetryableException.class,
                () -> service(chat).grade(invalid, submission())).code());
        verifyNoInteractions(chat);
    }

    @Test
    void legacyDescriptionsAreReferenceTextWithoutFixedWeights() {
        ChatGateway chat = mock(ChatGateway.class);
        Assignment legacy = new Assignment(1L, "Title", "Question",
                "[{\"key\":\"c1\",\"description\":\"直角三角形\",\"points\":100}]", 100, 1L);
        when(chat.complete(anyList())).thenAnswer(invocation -> {
            List<ChatGateway.ChatMessage> messages = invocation.getArgument(0);
            assertTrue(messages.get(1).content().contains("直角三角形"));
            assertFalse(messages.get(1).content().contains("points"));
            return "{\"score\":87,\"rationale\":\"公式正确，解释仍不完整\"}";
        });
        assertEquals(87, service(chat).grade(legacy, submission()).score());
    }

    @Test
    void dependencyFailuresRetainRetryClassification() {
        ChatGateway chat = mock(ChatGateway.class);
        when(chat.complete(anyList())).thenThrow(new RuntimeException("synthetic unavailable"));
        assertEquals("MODEL_UNAVAILABLE", assertThrows(GradingRetryableException.class,
                () -> service(chat).grade(assignment(), submission())).code());
        org.mockito.Mockito.reset(chat);
        when(chat.complete(anyList())).thenThrow(new DependencyRejectedException("synthetic rejection"));
        assertEquals("MODEL_REQUEST_REJECTED", assertThrows(GradingNonRetryableException.class,
                () -> service(chat).grade(assignment(), submission())).code());
    }

    private GradingModelService service(ChatGateway chat) {
        ObjectMapper mapper = tools.jackson.databind.json.JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        return new GradingModelService(chat, appProperties(), gradingProperties(),
                new RubricCodec(mapper), mapper);
    }

    private Assignment assignment() {
        Assignment assignment = new Assignment(1L, "勾股定理", "解释勾股定理", "[]", 100, 1L);
        assignment.setRubricSources("[]", "直角三角形的两直角边 a、b 与斜边 c 满足 a²+b²=c²。");
        return assignment;
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
                "v4", "grading.events", "grading.tasks", "grading.dlq",
                "grading.dispatch", "grading.retry", "grading.dead-letter");
    }
}
