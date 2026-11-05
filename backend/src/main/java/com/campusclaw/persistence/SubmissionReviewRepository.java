package com.campusclaw.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionReviewRepository extends JpaRepository<SubmissionReview, Long> {
    Optional<SubmissionReview> findBySubmissionId(Long submissionId);
    List<SubmissionReview> findBySubmissionIdIn(Collection<Long> submissionIds);
}
