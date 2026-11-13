package com.campusclaw.grading;

import java.util.List;

public record GradingResultView(
        int score,
        int maxScore,
        List<CriterionResult> criteria,
        String rationale,
        String method) {

    public GradingResultView(int score, int maxScore, List<CriterionResult> criteria, String rationale) {
        this(score, maxScore, criteria, rationale, "AI");
    }

    public record CriterionResult(
            String key,
            String description,
            int points,
            String verdict,
            String evidence,
            String explanation,
            int awardedPoints) {
    }
}
