package com.campusclaw.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.campusclaw.common.BadRequestException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LearningRulesTest {
    @Test
    void comparesFractionsExactlyWithoutRoundingOrExpressionExecution() {
        assertThat(LearningRules.correct("NUMBER", "3/4", "0.75")).isTrue();
        assertThat(LearningRules.correct("NUMBER", "-2/-3", "2/3")).isTrue();
        assertThat(LearningRules.correct("NUMBER", "0.333333333333", "1/3")).isFalse();
        assertThat(LearningRules.correct("CHOICE", "a", "A")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1/0", "1/2/3", "sqrt(81)", "1e1000000000", "NaN", "0.1234567890123", "9999999999999999999"})
    void refusesUnsafeOrUnboundedNumericAnswers(String input) {
        assertThatThrownBy(() -> LearningRules.normalizeAnswer("NUMBER", input)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void unknownIsNotZeroAndOnlyLatestTwentyEvidenceItemsCount() {
        var points = List.of(point(1L), point(2L));
        List<Map<String, Object>> evidence = new ArrayList<>();
        evidence.add(evidence(0));
        for (int i = 0; i < 20; i++) evidence.add(evidence(100));
        var result = LearningRules.profile(points, evidence);
        assertThat(result.get(0)).containsEntry("mastery", 100.0).containsEntry("sampleCount", 20)
                .containsEntry("totalCount", 21).containsEntry("state", "MASTERED");
        assertThat(result.get(1)).containsEntry("mastery", null).containsEntry("sampleCount", 0).containsEntry("state", "UNKNOWN");
    }

    @Test
    void enforcesThresholdBoundariesAndPrioritizesFlatTags() {
        var evidence = List.of(evidence(60));
        assertThat(LearningRules.profile(List.of(point(1L)), evidence).get(0)).containsEntry("state", "DEVELOPING");
        assertThat(LearningRules.profile(List.of(point(1L)), List.of(evidence(80))).get(0)).containsEntry("state", "MASTERED");
        var points = List.of(point(3L), point(2L), point(1L));
        assertThat(LearningRules.priorities(LearningRules.profile(points, evidence)))
                .extracting(row -> row.get("id")).containsExactly(2L, 3L, 1L);
    }

    private Map<String, Object> point(long id) { return Map.of("id", id, "name", "Math", "enabled", true); }
    private Map<String, Object> evidence(int score) {
        return Map.of("pointId", 1L, "score", score, "maxScore", 100, "occurredAt", "2026-11-10T00:00:00Z");
    }
}
