package com.stravamirror.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

class DetailIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void wireStrava(DynamicPropertyRegistry registry) {
        registry.add("strava.base-url", () -> "http://localhost:" + wireMock.port() + "/api/v3");
    }

    @BeforeEach
    void cleanUp() {
        wireMock.resetAll();
        jdbcTemplate.execute("DELETE FROM activity_details");
        jdbcTemplate.execute("DELETE FROM activity_summaries");
    }

    private static final String DETAIL_JSON =
            "{\"id\":500,\"name\":\"Morning Run\",\"distance\":5000,"
                    + "\"start_date\":\"2024-03-15T08:00:00Z\"}";

    @Test
    @DisplayName("AC-3.1 FR-016: cache miss fetches from Strava, stores, and returns detail")
    void cacheMissFetchesAndStores() throws Exception {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock
                .get(urlPathEqualTo("/api/v3/activities/500"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(DETAIL_JSON)));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/activities/500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activity_id").value(500))
                .andExpect(jsonPath("$.start_time").value("2024-03-15T08:00:00Z"));

        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM activity_details WHERE activity_id = 500", Long.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-3.2 FR-015: cache hit returns locally, 0 Strava calls")
    void cacheHitReturnsLocally() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO activity_details (activity_id, start_date, detail_json) VALUES "
                        + "(600, '2024-04-10T09:00:00Z', ?::jsonb)",
                "{\"id\":600,\"name\":\"Cached Run\",\"start_date\":\"2024-04-10T09:00:00Z\"}");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/activities/600"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activity_id").value(600));

        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/api/v3/activities/600")));
    }

    @Test
    @DisplayName("AC-3.3 FR-016: repeat request calls Strava only once total")
    void repeatRequestCallsStravaOnce() throws Exception {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock
                .get(urlPathEqualTo("/api/v3/activities/700"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":700,\"name\":\"Run\",\"start_date\":\"2024-05-01T07:00:00Z\"}")));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/activities/700"))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/activities/700"))
                .andExpect(status().isOk());

        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/api/v3/activities/700")));
    }

    @Test
    @DisplayName("AC-3.7: Strava 404 returns ACTIVITY_NOT_FOUND, nothing stored")
    void strava404ReturnsNotFound() throws Exception {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock
                .get(urlPathEqualTo("/api/v3/activities/999"))
                .willReturn(aResponse().withStatus(404)));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/activities/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACTIVITY_NOT_FOUND"));

        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM activity_details WHERE activity_id = 999", Long.class);
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("AC-3.8: Strava 401 returns STRAVA_AUTH_ERROR")
    void strava401ReturnsAuthError() throws Exception {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock
                .get(urlPathEqualTo("/api/v3/activities/800"))
                .willReturn(aResponse().withStatus(401)));

        MvcResult result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/activities/800"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("STRAVA_AUTH_ERROR"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("test-token-for-integration");
    }

    @Test
    @DisplayName("AC-3.9: Strava 429 returns STRAVA_RATE_LIMITED")
    void strava429ReturnsRateLimited() throws Exception {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock
                .get(urlPathEqualTo("/api/v3/activities/801"))
                .willReturn(aResponse().withStatus(429)));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/activities/801"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("STRAVA_RATE_LIMITED"));
    }

    @Test
    @DisplayName("AC-3.10: Strava 500 returns STRAVA_SERVER_ERROR")
    void strava500ReturnsServerError() throws Exception {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock
                .get(urlPathEqualTo("/api/v3/activities/802"))
                .willReturn(aResponse().withStatus(500)));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/activities/802"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("STRAVA_SERVER_ERROR"));
    }

    @Test
    @DisplayName("AC-3.11: Strava timeout returns STRAVA_TIMEOUT")
    void stravaTimeoutReturnsTimeout() throws Exception {
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock
                .get(urlPathEqualTo("/api/v3/activities/803"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(15000)));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/activities/803"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("STRAVA_TIMEOUT"));
    }
}
