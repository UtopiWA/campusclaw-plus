package com.campusclaw.grading;

import java.util.List;

public record GradingResultView(
        int score,
        int maxScore,
        List<CriterionResult> criteria,
        String rationale) {

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
