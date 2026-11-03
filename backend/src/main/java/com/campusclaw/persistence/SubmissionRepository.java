package com.campusclaw.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    Optional<Submission> findFirstByAssignmentIdAndStudentIdAndContentHash(
            Long assignmentId, Long studentId, String contentHash);

    Optional<Submission> findFirstByAssignmentIdAndStudentIdAndClientKey(
            Long assignmentId, Long studentId, String clientKey);

    Optional<Submission> findFirstByAssignmentIdAndStudentIdOrderByVersionDesc(
            Long assignmentId, Long studentId);

    List<Submission> findByAssignmentIdAndClassIdOrderByStudentIdAscVersionDesc(
            Long assignmentId, Long classId);

    List<Submission> findByStudentIdAndAssignmentIdInOrderByIdDesc(
            Long studentId, Collection<Long> assignmentIds);

    @Query("select coalesce(max(s.version), 0) from Submission s "
            + "where s.assignmentId = :assignmentId and s.studentId = :studentId")
    int findMaxVersion(@Param("assignmentId") Long assignmentId, @Param("studentId") Long studentId);
}
