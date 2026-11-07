package com.stravamirror.service;

import com.stravamirror.client.StravaClient;
import com.stravamirror.dto.ActivityDetailResponse;
import com.stravamirror.dto.ActivityListResponse;
import com.stravamirror.dto.ActivitySummaryDto;
import com.stravamirror.dto.SyncResponse;
import com.stravamirror.entity.ActivityDetail;
import com.stravamirror.entity.ActivitySummary;
import com.stravamirror.repository.ActivityDetailRepository;
import com.stravamirror.repository.ActivitySummaryRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ActivityService {

    private final StravaClient stravaClient;
    private final ActivitySummaryRepository summaryRepository;
    private final ActivityDetailRepository detailRepository;
    private final ObjectMapper objectMapper;

    public ActivityService(StravaClient stravaClient,
                           ActivitySummaryRepository summaryRepository,
                           ActivityDetailRepository detailRepository,
                           ObjectMapper objectMapper) {
        this.stravaClient = stravaClient;
        this.summaryRepository = summaryRepository;
        this.detailRepository = detailRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SyncResponse syncActivities(Long after, Long before, Integer page, Integer perPage) {
        String json = stravaClient.getAthleteActivities(after, before, page, perPage);
        JsonNode arrayNode = objectMapper.readTree(json);

        List<ActivitySummary> activities = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            long activityId = node.get("id").asLong();
            String startDateStr = node.get("start_date").asText();
            Instant startDate = Instant.parse(startDateStr);
            String summaryJson = objectMapper.writeValueAsString(node);
            activities.add(new ActivitySummary(activityId, startDate, summaryJson));
        }

        int total = activities.size();
        int stored = summaryRepository.bulkInsertOnConflictDoNothing(activities);
        int skipped = total - stored;

        return new SyncResponse(total, stored, skipped);
    }

    @Transactional(readOnly = true)
    public ActivityListResponse listActivities(Long after, Long before, int page, int perPage) {
        Instant afterInstant = (after != null) ? Instant.ofEpochSecond(after) : Instant.EPOCH;
        Instant beforeInstant = (before != null) ? Instant.ofEpochSecond(before) : Instant.parse("9999-12-31T23:59:59Z");

        Pageable pageable = PageRequest.of(page - 1, perPage);
        Page<ActivitySummary> resultPage = summaryRepository.findByDateRange(afterInstant, beforeInstant, pageable);

        List<ActivitySummaryDto> dtos = resultPage.getContent().stream()
                .map(this::toSummaryDto)
                .toList();

        return new ActivityListResponse(
                dtos,
                resultPage.getTotalElements(),
                page,
                perPage,
                resultPage.hasNext()
        );
    }

    @Transactional
    public ActivityDetailResponse getActivityDetail(long id) {
        Optional<ActivityDetail> cached = detailRepository.findById(id);
        if (cached.isPresent()) {
            return toDetailResponse(cached.get());
        }

        String json = stravaClient.getActivityDetail(id);
        JsonNode node = objectMapper.readTree(json);
        String startDateStr = node.get("start_date").asText();
        Instant startDate = Instant.parse(startDateStr);

        ActivityDetail detail = new ActivityDetail(id, startDate, json);
        detailRepository.save(detail);

        return toDetailResponse(detail);
    }

    private ActivitySummaryDto toSummaryDto(ActivitySummary entity) {
        Object summaryObj = objectMapper.readTree(entity.getSummaryJson());
        return new ActivitySummaryDto(
                entity.getActivityId(),
                entity.getStartDate().toString(),
                summaryObj
        );
    }

    private ActivityDetailResponse toDetailResponse(ActivityDetail entity) {
        Object detailObj = objectMapper.readTree(entity.getDetailJson());
        return new ActivityDetailResponse(
                entity.getActivityId(),
                entity.getStartDate().toString(),
                detailObj
        );
    }
}
