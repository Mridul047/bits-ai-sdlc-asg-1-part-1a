package com.stravamirror.dto;

import java.util.List;

public record ActivityListResponse(
    List<ActivitySummaryDto> activities,
    long totalCount,
    int page,
    int perPage,
    boolean hasNext
) {
    public ActivityListResponse {
        activities = List.copyOf(activities);
    }
}
