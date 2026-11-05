package com.campusclaw.grading;

import java.time.Instant;

public record TeacherSubmissionView(
        Long submissionId,
        Long studentId,
        String studentUsername,
        int version,
        String content,
        Instant createdAt,
        String taskId,
        String status,
        Integer score,
        Integer maxScore,
        String failureCode,
        GradingResultView aiResult,
        TeacherReviewView teacherReview) {
}
