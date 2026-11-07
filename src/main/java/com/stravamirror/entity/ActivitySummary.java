package com.stravamirror.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "activity_summaries")
public class ActivitySummary {

    @Id
    @Column(name = "activity_id")
    private Long activityId;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary_json", nullable = false, columnDefinition = "jsonb")
    private String summaryJson;

    protected ActivitySummary() {
    }

    public ActivitySummary(Long activityId, Instant startDate, String summaryJson) {
        this.activityId = activityId;
        this.startDate = startDate;
        this.summaryJson = summaryJson;
    }

    public Long getActivityId() {
        return activityId;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public String getSummaryJson() {
        return summaryJson;
    }
}
