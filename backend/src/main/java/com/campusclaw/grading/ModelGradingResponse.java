package com.campusclaw.grading;

import java.util.List;

public record ModelGradingResponse(List<ModelCriterion> criteria, String overallRationale) {
    public record ModelCriterion(String key, String verdict, String evidence, String explanation) {
    }
}
