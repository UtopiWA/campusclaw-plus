package com.campusclaw.grading;

import com.campusclaw.persistence.SubmissionReview;
import java.time.Instant;

public record TeacherReviewView(int score, String comment, Instant reviewedAt) {
    public static TeacherReviewView from(SubmissionReview review) {
        return new TeacherReviewView(review.getScore(), review.getComment(), review.getReviewedAt());
    }
}
