package com.stravamirror.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "strava")
public record StravaProperties(
    @NotBlank String accessToken,
    String baseUrl
) {
    private static final String DEFAULT_BASE_URL = "https://www.strava.com/api/v3";

    public String resolvedBaseUrl() {
        return (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : DEFAULT_BASE_URL;
    }
}
