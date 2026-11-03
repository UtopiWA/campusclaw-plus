package com.campusclaw.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GradingOutboxRepository extends JpaRepository<GradingOutbox, Long> {
    @Query("select o from GradingOutbox o where o.status = :status and o.availableAt <= :now "
            + "order by o.id asc")
    List<GradingOutbox> findDue(@Param("status") GradingOutboxStatus status,
                                @Param("now") Instant now, Pageable pageable);
}
