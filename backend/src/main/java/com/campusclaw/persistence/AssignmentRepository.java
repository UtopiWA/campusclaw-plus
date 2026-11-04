package com.campusclaw.persistence;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    Optional<Assignment> findFirstByClassIdAndTitle(Long classId, String title);

    List<Assignment> findByClassIdAndGradableTrueOrderByCreatedAtDesc(Long classId);

    Optional<Assignment> findByIdAndClassIdAndGradableTrue(Long id, Long classId);

    Optional<Assignment> findByIdAndClassId(Long id, Long classId);

    /** 仅在提交短事务中串行分配版本，避免并发重复请求撞唯一键。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Assignment a where a.id = :id and a.classId = :classId and a.gradable = true")
    Optional<Assignment> lockForSubmission(@Param("id") Long id, @Param("classId") Long classId);
}
