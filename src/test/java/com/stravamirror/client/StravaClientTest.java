package com.stravamirror.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.stravamirror.exception.ErrorCode;
import com.stravamirror.exception.StravaApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@WireMockTest
class StravaClientTest {

    private StravaClient stravaClient;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);

        RestClient restClient = RestClient.builder()
                .baseUrl(wmInfo.getHttpBaseUrl() + "/api/v3")
                .defaultHeader("Authorization", "Bearer test-token")
                .requestFactory(factory)
                .build();

        stravaClient = new StravaClient(restClient);
    }

    @Nested
    @DisplayName("getAthleteActivities")
    class GetAthleteActivities {

        @Test
        @DisplayName("FR-001: returns activity JSON array on success")
        void returnsActivitiesOnSuccess() {
            String json = "[{\"id\":1,\"name\":\"Run\"},{\"id\":2,\"name\":\"Ride\"}]";
            com.github.tomakehurst.wiremock.client.WireMock.stubFor(
                    get(urlPathEqualTo("/api/v3/athlete/activities"))
                            .willReturn(aResponse()
                                    .withStatus(200)
                                    .withHeader("Content-Type", "application/json")
                                    .withBody(json)));

            String result = stravaClient.getAthleteActivities(null, null, null, null);
            assertThat(result).isEqualTo(json);
        }

        @Test
        @DisplayName("STRAVA_AUTH_ERROR: Strava 401 maps to STRAVA_AUTH_ERROR")
        void strava401MapsToAuthError() {
            com.github.tomakehurst.wiremock.client.WireMock.stubFor(
                    get(urlPathEqualTo("/api/v3/athlete/activities"))
                            .willReturn(aResponse().withStatus(401)));

            assertThatThrownBy(() -> stravaClient.getAthleteActivities(null, null, null, null))
                    .isInstanceOf(StravaApiException.class)
                    .satisfies(e -> assertThat(((StravaApiException) e).getErrorCode())
                            .isEqualTo(ErrorCode.STRAVA_AUTH_ERROR));
        }

        @Test
        @DisplayName("STRAVA_RATE_LIMITED: Strava 429 maps to STRAVA_RATE_LIMITED")
        void strava429MapsToRateLimited() {
            com.github.tomakehurst.wiremock.client.WireMock.stubFor(
                    get(urlPathEqualTo("/api/v3/athlete/activities"))
                            .willReturn(aResponse().withStatus(429)));

            assertThatThrownBy(() -> stravaClient.getAthleteActivities(null, null, null, null))
                    .isInstanceOf(StravaApiException.class)
                    .satisfies(e -> assertThat(((StravaApiException) e).getErrorCode())
                            .isEqualTo(ErrorCode.STRAVA_RATE_LIMITED));
        }

        @Test
        @DisplayName("STRAVA_SERVER_ERROR: Strava 500 maps to STRAVA_SERVER_ERROR")
        void strava500MapsToServerError() {
            com.github.tomakehurst.wiremock.client.WireMock.stubFor(
                    get(urlPathEqualTo("/api/v3/athlete/activities"))
                            .willReturn(aResponse().withStatus(500)));

            assertThatThrownBy(() -> stravaClient.getAthleteActivities(null, null, null, null))
                    .isInstanceOf(StravaApiException.class)
                    .satisfies(e -> assertThat(((StravaApiException) e).getErrorCode())
                            .isEqualTo(ErrorCode.STRAVA_SERVER_ERROR));
        }

        @Test
        @DisplayName("STRAVA_TIMEOUT: connection timeout maps to STRAVA_TIMEOUT")
        void timeoutMapsToStravaTimeout() {
            com.github.tomakehurst.wiremock.client.WireMock.stubFor(
                    get(urlPathEqualTo("/api/v3/athlete/activities"))
                            .willReturn(aResponse()
                                    .withStatus(200)
                                    .withFixedDelay(15000)));

            assertThatThrownBy(() -> stravaClient.getAthleteActivities(null, null, null, null))
                    .isInstanceOf(StravaApiException.class)
                    .satisfies(e -> assertThat(((StravaApiException) e).getErrorCode())
                            .isEqualTo(ErrorCode.STRAVA_TIMEOUT));
        }
    }

    @Nested
    @DisplayName("getActivityDetail")
    class GetActivityDetail {

        @Test
        @DisplayName("FR-015: returns activity detail JSON on success")
        void returnsDetailOnSuccess() {
            String json = "{\"id\":123,\"name\":\"Morning Run\",\"distance\":5000}";
            com.github.tomakehurst.wiremock.client.WireMock.stubFor(
                    get(urlPathEqualTo("/api/v3/activities/123"))
                            .willReturn(aResponse()
                                    .withStatus(200)
                                    .withHeader("Content-Type", "application/json")
                                    .withBody(json)));

            String result = stravaClient.getActivityDetail(123L);
            assertThat(result).isEqualTo(json);
        }

        @Test
        @DisplayName("STRAVA_AUTH_ERROR: Strava 401 on detail maps to STRAVA_AUTH_ERROR")
        void strava401OnDetailMapsToAuthError() {
            com.github.tomakehurst.wiremock.client.WireMock.stubFor(
                    get(urlPathEqualTo("/api/v3/activities/123"))
                            .willReturn(aResponse().withStatus(401)));

            assertThatThrownBy(() -> stravaClient.getActivityDetail(123L))
                    .isInstanceOf(StravaApiException.class)
                    .satisfies(e -> assertThat(((StravaApiException) e).getErrorCode())
                            .isEqualTo(ErrorCode.STRAVA_AUTH_ERROR));
        }

        @Test
        @DisplayName("ACTIVITY_NOT_FOUND: Strava 404 maps to ACTIVITY_NOT_FOUND")
        void strava404MapsToNotFound() {
            com.github.tomakehurst.wiremock.client.WireMock.stubFor(
                    get(urlPathEqualTo("/api/v3/activities/999"))
                            .willReturn(aResponse().withStatus(404)));

            assertThatThrownBy(() -> stravaClient.getActivityDetail(999L))
                    .isInstanceOf(StravaApiException.class)
                    .satisfies(e -> assertThat(((StravaApiException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ACTIVITY_NOT_FOUND));
        }

        @Test
        @DisplayName("STRAVA_RATE_LIMITED: Strava 429 on detail maps to STRAVA_RATE_LIMITED")
        void strava429OnDetailMapsToRateLimited() {
            com.github.tomakehurst.wiremock.client.WireMock.stubFor(
                    get(urlPathEqualTo("/api/v3/activities/123"))
                            .willReturn(aResponse().withStatus(429)));

            assertThatThrownBy(() -> stravaClient.getActivityDetail(123L))
                    .isInstanceOf(StravaApiException.class)
                    .satisfies(e -> assertThat(((StravaApiException) e).getErrorCode())
                            .isEqualTo(ErrorCode.STRAVA_RATE_LIMITED));
        }

        @Test
        @DisplayName("STRAVA_SERVER_ERROR: Strava 500 on detail maps to STRAVA_SERVER_ERROR")
        void strava500OnDetailMapsToServerError() {
            com.github.tomakehurst.wiremock.client.WireMock.stubFor(
                    get(urlPathEqualTo("/api/v3/activities/123"))
                            .willReturn(aResponse().withStatus(500)));

            assertThatThrownBy(() -> stravaClient.getActivityDetail(123L))
                    .isInstanceOf(StravaApiException.class)
                    .satisfies(e -> assertThat(((StravaApiException) e).getErrorCode())
                            .isEqualTo(ErrorCode.STRAVA_SERVER_ERROR));
        }

        @Test
        @DisplayName("STRAVA_TIMEOUT: timeout on detail maps to STRAVA_TIMEOUT")
        void timeoutOnDetailMapsToStravaTimeout() {
            com.github.tomakehurst.wiremock.client.WireMock.stubFor(
                    get(urlPathEqualTo("/api/v3/activities/123"))
                            .willReturn(aResponse()
                                    .withStatus(200)
                                    .withFixedDelay(15000)));

            assertThatThrownBy(() -> stravaClient.getActivityDetail(123L))
                    .isInstanceOf(StravaApiException.class)
                    .satisfies(e -> assertThat(((StravaApiException) e).getErrorCode())
                            .isEqualTo(ErrorCode.STRAVA_TIMEOUT));
        }
    }
}
