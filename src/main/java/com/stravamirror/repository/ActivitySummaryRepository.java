package com.stravamirror.repository;

import com.stravamirror.entity.ActivitySummary;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivitySummaryRepository extends JpaRepository<ActivitySummary, Long>, ActivitySummaryRepositoryCustom {

    @Query("SELECT a FROM ActivitySummary a WHERE "
            + "a.startDate > :after AND "
            + "a.startDate < :before "
            + "ORDER BY a.startDate DESC, a.activityId DESC")
    Page<ActivitySummary> findByDateRange(
            @Param("after") Instant after,
            @Param("before") Instant before,
            Pageable pageable);
}
