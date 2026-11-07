package com.stravamirror.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stravamirror.dto.ActivityDetailResponse;
import com.stravamirror.service.ActivityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ActivityController.class)
class ActivityControllerDetailTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActivityService activityService;

    @Test
    @DisplayName("FR-015: valid cached ID returns 200 with activity_id, start_time, detail")
    void validCachedIdReturns200() throws Exception {
        when(activityService.getActivityDetail(123L))
                .thenReturn(new ActivityDetailResponse(123L, "2024-01-15T08:30:00Z", null));

        mockMvc.perform(get("/activities/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activity_id").value(123))
                .andExpect(jsonPath("$.start_time").value("2024-01-15T08:30:00Z"));
    }

    @Test
    @DisplayName("FR-018: id=abc returns 400 VALIDATION_ERROR")
    void nonNumericIdReturns400() throws Exception {
        mockMvc.perform(get("/activities/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("FR-018: id=0 returns 400 VALIDATION_ERROR")
    void zeroIdReturns400() throws Exception {
        mockMvc.perform(get("/activities/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("FR-018: id=-1 returns 400 VALIDATION_ERROR")
    void negativeIdReturns400() throws Exception {
        mockMvc.perform(get("/activities/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
