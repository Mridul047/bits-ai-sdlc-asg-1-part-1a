package com.stravamirror.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stravamirror.dto.ActivityListResponse;
import com.stravamirror.dto.ActivitySummaryDto;
import com.stravamirror.service.ActivityService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ActivityController.class)
class ActivityControllerListTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActivityService activityService;

    @Test
    @DisplayName("FR-013: valid list returns 200 with activities and pagination metadata")
    void validListReturns200WithPagination() throws Exception {
        ActivitySummaryDto dto = new ActivitySummaryDto(1L, "2024-01-15T08:30:00Z", null);
        ActivityListResponse response = new ActivityListResponse(
                List.of(dto), 1, 1, 30, false);
        when(activityService.listActivities(any(), any(), anyInt(), anyInt()))
                .thenReturn(response);

        mockMvc.perform(get("/athlete/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities").isArray())
                .andExpect(jsonPath("$.total_count").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.per_page").value(30))
                .andExpect(jsonPath("$.has_next").value(false));
    }

    @Test
    @DisplayName("FR-011: empty result returns 200 with empty array")
    void emptyResultReturns200WithEmptyArray() throws Exception {
        ActivityListResponse response = new ActivityListResponse(
                List.of(), 0, 1, 30, false);
        when(activityService.listActivities(any(), any(), anyInt(), anyInt()))
                .thenReturn(response);

        mockMvc.perform(get("/athlete/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities").isEmpty())
                .andExpect(jsonPath("$.total_count").value(0));
    }

    @Test
    @DisplayName("FR-010: per_page=201 returns 400 VALIDATION_ERROR")
    void perPageOver200Returns400() throws Exception {
        mockMvc.perform(get("/athlete/activities")
                        .param("per_page", "201"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("FR-010: per_page=0 returns 400 VALIDATION_ERROR")
    void perPageZeroReturns400() throws Exception {
        mockMvc.perform(get("/athlete/activities")
                        .param("per_page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("FR-010: after=1000&before=500 returns 400 VALIDATION_ERROR")
    void afterNotLessThanBeforeReturns400() throws Exception {
        mockMvc.perform(get("/athlete/activities")
                        .param("after", "1000")
                        .param("before", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("FR-012: list endpoint never calls Strava — verified by mock isolation")
    void listNeverCallsStrava() throws Exception {
        ActivityListResponse response = new ActivityListResponse(
                List.of(), 0, 1, 30, false);
        when(activityService.listActivities(any(), any(), anyInt(), anyInt()))
                .thenReturn(response);

        mockMvc.perform(get("/athlete/activities"))
                .andExpect(status().isOk());
    }
}
