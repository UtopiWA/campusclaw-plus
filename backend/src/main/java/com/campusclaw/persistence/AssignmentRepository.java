package com.campusclaw.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    Optional<Assignment> findFirstByClassIdAndTitle(Long classId, String title);

    List<Assignment> findByClassIdAndGradableTrueOrderByCreatedAtDesc(Long classId);

    Optional<Assignment> findByIdAndClassIdAndGradableTrue(Long id, Long classId);

    Optional<Assignment> findByIdAndClassId(Long id, Long classId);
}
