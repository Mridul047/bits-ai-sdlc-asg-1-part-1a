package com.stravamirror.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "activity_details")
public class ActivityDetail {

    @Id
    @Column(name = "activity_id")
    private Long activityId;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail_json", nullable = false, columnDefinition = "jsonb")
    private String detailJson;

    protected ActivityDetail() {
    }

    public ActivityDetail(Long activityId, Instant startDate, String detailJson) {
        this.activityId = activityId;
        this.startDate = startDate;
        this.detailJson = detailJson;
    }

    public Long getActivityId() {
        return activityId;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public String getDetailJson() {
        return detailJson;
    }
}
