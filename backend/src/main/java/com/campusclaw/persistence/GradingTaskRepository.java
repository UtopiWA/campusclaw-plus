package com.campusclaw.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface GradingTaskRepository extends JpaRepository<GradingTask, String> {
    Optional<GradingTask> findByIdempotencyKey(String idempotencyKey);

    Optional<GradingTask> findFirstBySubmissionIdOrderByCreatedAtDesc(Long submissionId);

    List<GradingTask> findBySubmissionIdIn(Collection<Long> submissionIds);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update GradingTask t set t.status = com.campusclaw.persistence.GradingTaskStatus.RUNNING, "
            + "t.attempts = t.attempts + 1, t.startedAt = :now, t.updatedAt = :now "
            + "where t.taskId = :taskId and t.status = com.campusclaw.persistence.GradingTaskStatus.PENDING "
            + "and t.availableAt <= :now")
    int claim(@Param("taskId") String taskId, @Param("now") Instant now);

    List<GradingTask> findByStatusAndStartedAtBefore(GradingTaskStatus status, Instant cutoff);

    long countByStatus(GradingTaskStatus status);
}
