package com.stravamirror.repository;

import com.stravamirror.entity.ActivitySummary;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ActivitySummaryRepositoryImpl implements ActivitySummaryRepositoryCustom {

    private final JdbcTemplate jdbcTemplate;

    public ActivitySummaryRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int bulkInsertOnConflictDoNothing(List<ActivitySummary> activities) {
        if (activities.isEmpty()) {
            return 0;
        }

        int totalInserted = 0;
        for (ActivitySummary activity : activities) {
            int rows = jdbcTemplate.update(
                    "INSERT INTO activity_summaries (activity_id, start_date, summary_json) "
                            + "VALUES (?, ?, ?::jsonb) ON CONFLICT (activity_id) DO NOTHING",
                    activity.getActivityId(),
                    Timestamp.from(activity.getStartDate()),
                    activity.getSummaryJson()
            );
            totalInserted += rows;
        }
        return totalInserted;
    }
}
