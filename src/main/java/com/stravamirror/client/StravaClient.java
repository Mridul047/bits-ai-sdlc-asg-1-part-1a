package com.stravamirror.client;

import com.stravamirror.exception.ErrorCode;
import com.stravamirror.exception.StravaApiException;
import java.net.SocketTimeoutException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class StravaClient {

    private final RestClient restClient;

    public StravaClient(RestClient stravaRestClient) {
        this.restClient = stravaRestClient;
    }

    public String getAthleteActivities(Long after, Long before, Integer page, Integer perPage) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/athlete/activities");
                        if (after != null) {
                            uriBuilder.queryParam("after", after);
                        }
                        if (before != null) {
                            uriBuilder.queryParam("before", before);
                        }
                        if (page != null) {
                            uriBuilder.queryParam("page", page);
                        }
                        if (perPage != null) {
                            uriBuilder.queryParam("per_page", perPage);
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw mapResponseException(e);
        } catch (ResourceAccessException e) {
            throw mapAccessException(e);
        }
    }

    public String getActivityDetail(long id) {
        try {
            return restClient.get()
                    .uri("/activities/{id}", id)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw mapResponseException(e);
        } catch (ResourceAccessException e) {
            throw mapAccessException(e);
        }
    }

    private StravaApiException mapResponseException(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        if (status == 401) {
            return new StravaApiException(ErrorCode.STRAVA_AUTH_ERROR,
                    "Strava rejected the access token");
        }
        if (status == 404) {
            return new StravaApiException(ErrorCode.ACTIVITY_NOT_FOUND,
                    "Activity not found on Strava");
        }
        if (status == 429) {
            return new StravaApiException(ErrorCode.STRAVA_RATE_LIMITED,
                    "Strava rate limit exceeded");
        }
        if (status >= 500) {
            return new StravaApiException(ErrorCode.STRAVA_SERVER_ERROR,
                    "Strava server error");
        }
        return new StravaApiException(ErrorCode.STRAVA_SERVER_ERROR,
                "Unexpected Strava response: " + status);
    }

    private StravaApiException mapAccessException(ResourceAccessException e) {
        if (e.getCause() instanceof SocketTimeoutException) {
            return new StravaApiException(ErrorCode.STRAVA_TIMEOUT,
                    "Strava did not respond in time", e);
        }
        return new StravaApiException(ErrorCode.STRAVA_TIMEOUT,
                "Unable to reach Strava", e);
    }
}
