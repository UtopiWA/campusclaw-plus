package com.campusclaw.grading;

import com.campusclaw.common.BadRequestException;
import com.campusclaw.common.NotFoundException;
import com.campusclaw.persistence.AssignmentRepository;
import com.campusclaw.persistence.Role;
import com.campusclaw.persistence.SubmissionRepository;
import com.campusclaw.persistence.SubmissionReview;
import com.campusclaw.persistence.SubmissionReviewRepository;
import com.campusclaw.persistence.UserAccount;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmissionReviewService {
    private final SubmissionRepository submissions;
    private final SubmissionReviewRepository reviews;
    private final AssignmentRepository assignments;

    public SubmissionReviewService(SubmissionRepository submissions, SubmissionReviewRepository reviews,
                                   AssignmentRepository assignments) {
        this.submissions = submissions;
        this.reviews = reviews;
        this.assignments = assignments;
    }

    @Transactional
    public TeacherReviewView review(Long assignmentId, Long submissionId,
                                    ReviewSubmissionRequest request, UserAccount teacher) {
        if (teacher.getRole() != Role.TEACHER) throw new AccessDeniedException("Teacher role required");
        var assignment = assignments.findByIdAndClassId(assignmentId, teacher.getClassId())
                .filter(a -> a.isGradable() && !a.isPersonalPractice()).orElseThrow(NotFoundException::new);
        var submission = submissions.lockForReview(submissionId, assignmentId, teacher.getClassId())
                .orElseThrow(NotFoundException::new);
        if (request.score() == null || request.score() < 0 || request.score() > assignment.getMaxScore()
                || request.comment() == null || request.comment().isBlank()
                || request.comment().codePointCount(0, request.comment().length()) > 2000) {
            throw new BadRequestException("终评分数应在 0～作业总分之间，评语为 1～2000 字");
        }
        SubmissionReview review = reviews.findBySubmissionId(submissionId)
                .orElseGet(() -> new SubmissionReview(submission));
        review.update(teacher.getId(), request.score(), request.comment().trim());
        return TeacherReviewView.from(reviews.saveAndFlush(review));
    }
}
