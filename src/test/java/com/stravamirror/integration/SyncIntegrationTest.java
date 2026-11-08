package com.stravamirror.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

class SyncIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void wireStrava(DynamicPropertyRegistry registry) {
        registry.add("strava.base-url", () -> "http://localhost:" + wireMock.port() + "/api/v3");
    }

    @BeforeEach
    void cleanUp() {
        wireMock.resetAll();
        jdbcTemplate.execute("DELETE FROM activity_summaries");
        jdbcTemplate.execute("DELETE FROM activity_details");
    }

    private String stravaActivitiesJson(long... ids) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"id\":").append(ids[i])
                    .append(",\"name\":\"Activity ").append(ids[i])
                    .append("\",\"start_date\":\"2024-01-")
                    .append(String.format("%02d", (i % 28) + 1))
                    .append("T10:00:00Z\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    @Test
    @DisplayName("AC-1.1 FR-002: sync 3 new activities — stored=3, Strava called exactly once")
    void syncThreeNewActivities() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/api/v3/athlete/activities"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(stravaActivitiesJson(100, 101, 102))));

        mockMvc.perform(post("/athlete/activities/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.stored").value(3))
                .andExpect(jsonPath("$.skipped").value(0));

        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/api/v3/athlete/activities")));
    }

    @Test
    @DisplayName("AC-1.2 FR-004: re-syncing same 3 — skipped=3, stored=0")
    void reSyncSameThreeActivities() throws Exception {
        String json = stravaActivitiesJson(200, 201, 202);
        wireMock.stubFor(get(urlPathEqualTo("/api/v3/athlete/activities"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(json)));

        mockMvc.perform(post("/athlete/activities/sync"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/athlete/activities/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.stored").value(0))
                .andExpect(jsonPath("$.skipped").value(3));
    }

    @Test
    @DisplayName("AC-1.3 FR-004: sync 5 where 2 exist — stored=3, skipped=2")
    void syncFiveWithTwoExisting() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/api/v3/athlete/activities"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(stravaActivitiesJson(300, 301))));
        mockMvc.perform(post("/athlete/activities/sync"))
                .andExpect(status().isOk());

        wireMock.resetAll();
        wireMock.stubFor(get(urlPathEqualTo("/api/v3/athlete/activities"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(stravaActivitiesJson(300, 301, 302, 303, 304))));

        mockMvc.perform(post("/athlete/activities/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.stored").value(3))
                .andExpect(jsonPath("$.skipped").value(2));
    }

    @Test
    @DisplayName("AC-1.4 FR-005: empty Strava response — all zeros")
    void syncEmptyResponse() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/api/v3/athlete/activities"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        mockMvc.perform(post("/athlete/activities/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.stored").value(0))
                .andExpect(jsonPath("$.skipped").value(0));
    }

    @Test
    @DisplayName("AC-1.11 FR-007 FR-022: Strava 401 returns STRAVA_AUTH_ERROR, zero stored")
    void strava401ReturnsAuthError() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/api/v3/athlete/activities"))
                .willReturn(aResponse().withStatus(401)));

        MvcResult result = mockMvc.perform(post("/athlete/activities/sync"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("STRAVA_AUTH_ERROR"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("test-token-for-integration");

        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM activity_summaries", Long.class);
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("AC-1.12: Strava 429 returns STRAVA_RATE_LIMITED")
    void strava429ReturnsRateLimited() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/api/v3/athlete/activities"))
                .willReturn(aResponse().withStatus(429)));

        mockMvc.perform(post("/athlete/activities/sync"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("STRAVA_RATE_LIMITED"));
    }

    @Test
    @DisplayName("AC-1.13: Strava 500 returns STRAVA_SERVER_ERROR (502)")
    void strava500ReturnsServerError() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/api/v3/athlete/activities"))
                .willReturn(aResponse().withStatus(500)));

        mockMvc.perform(post("/athlete/activities/sync"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("STRAVA_SERVER_ERROR"));
    }

    @Test
    @DisplayName("AC-1.14: Strava timeout returns STRAVA_TIMEOUT (504)")
    void stravaTimeoutReturnsTimeout() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/api/v3/athlete/activities"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(15000)));

        mockMvc.perform(post("/athlete/activities/sync"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("STRAVA_TIMEOUT"));
    }
}
