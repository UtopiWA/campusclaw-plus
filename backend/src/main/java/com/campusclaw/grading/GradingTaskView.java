package com.campusclaw.grading;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GradingTaskView(
        String taskId,
        Long submissionId,
        Long assignmentId,
        String status,
        int attempts,
        int maxAttempts,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        String lastFailureCode,
        String failureCode,
        GradingResultView result,
        String submissionContent,
        TeacherReviewView teacherReview) {
}
