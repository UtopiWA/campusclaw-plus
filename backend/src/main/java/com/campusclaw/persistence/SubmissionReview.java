package com.campusclaw.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 教师终评独立保存，后台任务只能写入原始 AI 结果。 */
@Entity
@Table(name = "submission_reviews")
public class SubmissionReview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;
    @Column(name = "submission_id", nullable = false)
    private Long submissionId;
    @Column(name = "class_id", nullable = false)
    private Long classId;
    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;
    @Column(nullable = false)
    private int score;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String comment;
    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    protected SubmissionReview() { }

    public SubmissionReview(Submission submission) {
        this.submissionId = submission.getId();
        this.assignmentId = submission.getAssignmentId();
        this.classId = submission.getClassId();
    }

    public void update(Long teacherId, int score, String comment) {
        this.teacherId = teacherId;
        this.score = score;
        this.comment = comment;
        this.reviewedAt = Instant.now();
    }

    public Long getSubmissionId() { return submissionId; }
    public int getScore() { return score; }
    public String getComment() { return comment; }
    public Instant getReviewedAt() { return reviewedAt; }
}
