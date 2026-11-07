package com.stravamirror.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stravamirror.controller.ActivityController;
import com.stravamirror.service.ActivityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(ActivityController.class)
class ErrorResponseSecurityTest {

    private static final String TEST_TOKEN = "super-secret-token-value";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActivityService activityService;

    @ParameterizedTest(name = "EC-3: {0} error response does not contain token")
    @EnumSource(ErrorCode.class)
    @DisplayName("EC-3: error responses do not contain the configured token")
    void errorResponseDoesNotContainToken(ErrorCode errorCode) throws Exception {
        when(activityService.syncActivities(any(), any(), any(), any()))
                .thenThrow(new StravaApiException(errorCode, "Test error for " + errorCode.name()));

        MvcResult result = mockMvc.perform(post("/athlete/activities/sync"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(TEST_TOKEN);
    }

    @Test
    @DisplayName("EC-4: DATABASE_ERROR response does not contain raw stack trace")
    void databaseErrorDoesNotContainStackTrace() throws Exception {
        when(activityService.syncActivities(any(), any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("Connection refused"));

        MvcResult result = mockMvc.perform(post("/athlete/activities/sync"))
                .andExpect(status().isInternalServerError())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("Connection refused");
        assertThat(body).doesNotContain("DataAccessResourceFailureException");
        assertThat(body).doesNotContain("at com.stravamirror");
    }

    @Test
    @DisplayName("EC-4: STRAVA_SERVER_ERROR does not contain raw Strava response body")
    void stravaErrorDoesNotContainRawBody() throws Exception {
        when(activityService.syncActivities(any(), any(), any(), any()))
                .thenThrow(new StravaApiException(ErrorCode.STRAVA_SERVER_ERROR, "Strava server error"));

        MvcResult result = mockMvc.perform(post("/athlete/activities/sync"))
                .andExpect(status().isBadGateway())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(TEST_TOKEN);
    }
}
