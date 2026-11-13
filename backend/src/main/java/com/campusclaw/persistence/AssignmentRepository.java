package com.campusclaw.persistence;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    @Query(value = "SELECT a.id AS assignmentId,p.id,p.name FROM assignments a "
            + "JOIN learning_assignment_points l ON l.assignment_id=a.id AND l.class_id=a.class_id "
            + "JOIN learning_points p ON p.id=l.point_id AND p.class_id=a.class_id "
            + "WHERE a.class_id=:classId AND a.personal_practice=FALSE ORDER BY p.id", nativeQuery = true)
    List<java.util.Map<String, Object>> findTagsByClassId(@Param("classId") Long classId);

    Optional<Assignment> findFirstByClassIdAndTitle(Long classId, String title);

    @Query("select a from Assignment a where a.classId=:classId and a.gradable=true and a.personalPractice=false order by a.createdAt desc")
    List<Assignment> findByClassIdAndGradableTrueOrderByCreatedAtDesc(Long classId);

    Optional<Assignment> findByIdAndClassIdAndGradableTrue(Long id, Long classId);

    Optional<Assignment> findByIdAndClassId(Long id, Long classId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Assignment a where a.id=:id and a.classId=:classId and a.gradable=true and a.personalPractice=true")
    Optional<Assignment> lockForPractice(@Param("id") Long id, @Param("classId") Long classId);

    /** 仅在提交短事务中串行分配版本，避免并发重复请求撞唯一键。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Assignment a where a.id = :id and a.classId = :classId and a.gradable = true and a.personalPractice = false")
    Optional<Assignment> lockForSubmission(@Param("id") Long id, @Param("classId") Long classId);
}
