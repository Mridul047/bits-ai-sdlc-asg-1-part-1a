package com.stravamirror.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stravamirror.dto.SyncResponse;
import com.stravamirror.exception.GlobalExceptionHandler;
import com.stravamirror.service.ActivityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ActivityController.class)
class ActivityControllerSyncTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActivityService activityService;

    @Test
    @DisplayName("FR-005: valid sync returns 200 with total, stored, skipped")
    void validSyncReturns200WithCounts() throws Exception {
        when(activityService.syncActivities(any(), any(), any(), any()))
                .thenReturn(new SyncResponse(5, 3, 2));

        mockMvc.perform(post("/athlete/activities/sync")
                        .param("after", "1000")
                        .param("before", "2000")
                        .param("page", "1")
                        .param("per_page", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.stored").value(3))
                .andExpect(jsonPath("$.skipped").value(2));
    }

    @Test
    @DisplayName("FR-006: after=abc returns 400 VALIDATION_ERROR")
    void nonNumericAfterReturns400() throws Exception {
        mockMvc.perform(post("/athlete/activities/sync")
                        .param("after", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("FR-006: after=-1 returns 400 VALIDATION_ERROR")
    void negativeAfterReturns400() throws Exception {
        mockMvc.perform(post("/athlete/activities/sync")
                        .param("after", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("FR-006: after=1000&before=500 returns 400 VALIDATION_ERROR")
    void afterNotLessThanBeforeReturns400() throws Exception {
        mockMvc.perform(post("/athlete/activities/sync")
                        .param("after", "1000")
                        .param("before", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("FR-006: after=1000&before=1000 returns 400 VALIDATION_ERROR")
    void afterEqualToBeforeReturns400() throws Exception {
        mockMvc.perform(post("/athlete/activities/sync")
                        .param("after", "1000")
                        .param("before", "1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("FR-006: page=0 returns 400 VALIDATION_ERROR")
    void pageZeroReturns400() throws Exception {
        mockMvc.perform(post("/athlete/activities/sync")
                        .param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("FR-006: per_page=-1 returns 400 VALIDATION_ERROR")
    void negativePerPageReturns400() throws Exception {
        mockMvc.perform(post("/athlete/activities/sync")
                        .param("per_page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("FR-006: per_page=201 returns 400 VALIDATION_ERROR")
    void perPageOver200Returns400() throws Exception {
        mockMvc.perform(post("/athlete/activities/sync")
                        .param("per_page", "201"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
