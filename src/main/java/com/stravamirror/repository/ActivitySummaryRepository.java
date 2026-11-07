package com.stravamirror.repository;

import com.stravamirror.entity.ActivitySummary;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivitySummaryRepository extends JpaRepository<ActivitySummary, Long>, ActivitySummaryRepositoryCustom {

    @Query("SELECT a FROM ActivitySummary a WHERE "
            + "(:after IS NULL OR a.startDate > :after) AND "
            + "(:before IS NULL OR a.startDate < :before) "
            + "ORDER BY a.startDate DESC, a.activityId DESC")
    Page<ActivitySummary> findByDateRange(
            @Param("after") Instant after,
            @Param("before") Instant before,
            Pageable pageable);
}
