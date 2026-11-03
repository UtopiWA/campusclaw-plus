package com.campusclaw.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradingResultRepository extends JpaRepository<GradingResult, Long> {
    Optional<GradingResult> findByTaskId(String taskId);

    Optional<GradingResult> findBySubmissionIdAndVersion(Long submissionId, int version);

    List<GradingResult> findBySubmissionIdIn(Collection<Long> submissionIds);
}
