package com.campusclaw.grading;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssignmentView(
        Long id,
        String title,
        String prompt,
        List<RubricCriterion> criteria,
        int maxScore,
        Instant createdAt,
        MyLatestSubmission myLatest,
        List<RubricSourceView> rubricSources) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MyLatestSubmission(
            Long submissionId,
            int version,
            String taskId,
            String status,
            Integer score,
            Integer maxScore,
            TeacherReviewView teacherReview) {
    }
}
