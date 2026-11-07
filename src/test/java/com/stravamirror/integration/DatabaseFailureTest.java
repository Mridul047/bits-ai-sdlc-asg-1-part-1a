package com.stravamirror.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stravamirror.controller.ActivityController;
import com.stravamirror.exception.GlobalExceptionHandler;
import com.stravamirror.service.ActivityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ActivityController.class)
@Import(GlobalExceptionHandler.class)
class DatabaseFailureTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActivityService activityService;

    @Test
    @DisplayName("EC-5: DB failure during sync returns 500 DATABASE_ERROR, zero stored")
    void dbFailureDuringSync() throws Exception {
        when(activityService.syncActivities(any(), any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("DB unavailable"));

        mockMvc.perform(post("/athlete/activities/sync"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("DATABASE_ERROR"));
    }

    @Test
    @DisplayName("EC-6: DB failure during list returns 500 DATABASE_ERROR")
    void dbFailureDuringList() throws Exception {
        when(activityService.listActivities(any(), any(), anyInt(), anyInt()))
                .thenThrow(new DataAccessResourceFailureException("DB unavailable"));

        mockMvc.perform(get("/athlete/activities"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("DATABASE_ERROR"));
    }

    @Test
    @DisplayName("EC-7: DB failure during detail store returns 500 DATABASE_ERROR")
    void dbFailureDuringDetailStore() throws Exception {
        when(activityService.getActivityDetail(anyLong()))
                .thenThrow(new DataAccessResourceFailureException("DB unavailable"));

        mockMvc.perform(get("/activities/123"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("DATABASE_ERROR"));
    }
}
