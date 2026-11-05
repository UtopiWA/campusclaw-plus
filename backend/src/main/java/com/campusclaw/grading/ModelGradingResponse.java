package com.campusclaw.grading;

/** 仅接收模型直接给出的分数和有依据的评语。 */
public record ModelGradingResponse(Integer score, String rationale) {
}
