package com.stravamirror.controller;

import com.stravamirror.dto.ActivityDetailResponse;
import com.stravamirror.dto.ActivityListResponse;
import com.stravamirror.dto.SyncResponse;
import com.stravamirror.exception.ErrorCode;
import com.stravamirror.exception.StravaApiException;
import com.stravamirror.service.ActivityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @PostMapping("/athlete/activities/sync")
    public ResponseEntity<SyncResponse> syncActivities(
            @RequestParam(required = false) Long after,
            @RequestParam(required = false) Long before,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage) {

        validateSyncAndListParams(after, before, page, perPage);

        SyncResponse response = activityService.syncActivities(after, before, page, perPage);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/athlete/activities")
    public ResponseEntity<ActivityListResponse> listActivities(
            @RequestParam(required = false) Long after,
            @RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(name = "per_page", defaultValue = "30") Integer perPage) {

        validateSyncAndListParams(after, before, page, perPage);

        ActivityListResponse response = activityService.listActivities(after, before, page, perPage);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/activities/{id}")
    public ResponseEntity<ActivityDetailResponse> getActivityDetail(@PathVariable Long id) {
        if (id == null || id < 1) {
            throw new StravaApiException(ErrorCode.VALIDATION_ERROR,
                    "Activity ID must be a positive integer");
        }

        ActivityDetailResponse response = activityService.getActivityDetail(id);
        return ResponseEntity.ok(response);
    }

    private void validateSyncAndListParams(Long after, Long before, Integer page, Integer perPage) {
        if (after != null && after < 0) {
            throw new StravaApiException(ErrorCode.VALIDATION_ERROR,
                    "Parameter 'after' must be non-negative");
        }
        if (before != null && before < 0) {
            throw new StravaApiException(ErrorCode.VALIDATION_ERROR,
                    "Parameter 'before' must be non-negative");
        }
        if (after != null && before != null && after >= before) {
            throw new StravaApiException(ErrorCode.VALIDATION_ERROR,
                    "Parameter 'after' must be strictly less than 'before'");
        }
        if (page != null && page < 1) {
            throw new StravaApiException(ErrorCode.VALIDATION_ERROR,
                    "Parameter 'page' must be at least 1");
        }
        if (perPage != null && (perPage < 1 || perPage > 200)) {
            throw new StravaApiException(ErrorCode.VALIDATION_ERROR,
                    "Parameter 'per_page' must be between 1 and 200");
        }
    }
}
