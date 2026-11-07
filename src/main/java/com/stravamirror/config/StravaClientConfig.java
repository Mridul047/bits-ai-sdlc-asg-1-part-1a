package com.stravamirror.config;

import java.time.Duration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class StravaClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    public RestClient stravaRestClient(StravaProperties stravaProperties) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withTimeouts(CONNECT_TIMEOUT, READ_TIMEOUT);
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.detect().build(settings);

        return RestClient.builder()
                .baseUrl(stravaProperties.resolvedBaseUrl())
                .defaultHeader("Authorization", "Bearer " + stravaProperties.accessToken())
                .requestFactory(factory)
                .build();
    }
}
