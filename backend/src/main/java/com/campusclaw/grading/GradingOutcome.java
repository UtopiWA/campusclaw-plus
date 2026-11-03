package com.campusclaw.grading;

public record GradingOutcome(
        int score,
        int maxScore,
        String criteriaJson,
        String rationale,
        String modelName,
        String promptVersion) {
}
