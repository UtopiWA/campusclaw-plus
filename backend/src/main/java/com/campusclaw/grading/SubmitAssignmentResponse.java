package com.campusclaw.grading;

public record SubmitAssignmentResponse(
        String taskId,
        String status,
        String statusUrl,
        Long submissionId,
        int version) {
}
