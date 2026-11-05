package com.campusclaw.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Submission s where s.id = :id and s.assignmentId = :assignmentId and s.classId = :classId")
    Optional<Submission> lockForReview(@Param("id") Long id, @Param("assignmentId") Long assignmentId,
                                      @Param("classId") Long classId);
    Optional<Submission> findFirstByAssignmentIdAndStudentIdAndContentHash(
            Long assignmentId, Long studentId, String contentHash);

    Optional<Submission> findFirstByAssignmentIdAndStudentIdAndClientKey(
            Long assignmentId, Long studentId, String clientKey);

    Optional<Submission> findFirstByAssignmentIdAndStudentIdOrderByVersionDesc(
            Long assignmentId, Long studentId);

    List<Submission> findByAssignmentIdAndClassIdOrderByStudentIdAscVersionDesc(
            Long assignmentId, Long classId, Pageable pageable);

    List<Submission> findByStudentIdAndAssignmentIdInOrderByIdDesc(
            Long studentId, Collection<Long> assignmentIds);

    @Query("select coalesce(max(s.version), 0) from Submission s "
            + "where s.assignmentId = :assignmentId and s.studentId = :studentId")
    int findMaxVersion(@Param("assignmentId") Long assignmentId, @Param("studentId") Long studentId);
}
