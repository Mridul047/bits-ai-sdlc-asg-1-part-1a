package com.stravamirror.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

class StartupValidationTest {

    @Test
    @DisplayName("EC-1 FR-024: application refuses to start when STRAVA_ACCESS_TOKEN is missing")
    void refusesStartWhenTokenMissing() {
        SpringApplication app = new SpringApplication(
                com.stravamirror.StravaActivityMirrorApplication.class);
        app.setAdditionalProfiles("test-no-token");

        assertThatThrownBy(() -> app.run(
                "--spring.datasource.url=jdbc:postgresql://localhost:5432/test",
                "--spring.datasource.username=test",
                "--spring.datasource.password=test",
                "--spring.flyway.enabled=false",
                "--spring.jpa.hibernate.ddl-auto=none"
        )).isNotNull();
    }

    @Test
    @DisplayName("EC-1 FR-024: application refuses to start when STRAVA_ACCESS_TOKEN is blank")
    void refusesStartWhenTokenBlank() {
        SpringApplication app = new SpringApplication(
                com.stravamirror.StravaActivityMirrorApplication.class);

        assertThatThrownBy(() -> app.run(
                "--strava.access-token=",
                "--spring.datasource.url=jdbc:postgresql://localhost:5432/test",
                "--spring.datasource.username=test",
                "--spring.datasource.password=test",
                "--spring.flyway.enabled=false",
                "--spring.jpa.hibernate.ddl-auto=none"
        )).isNotNull();
    }

    @Test
    @DisplayName("EC-2 FR-025: application refuses to start when database URL is missing")
    void refusesStartWhenDatabaseUrlMissing() {
        SpringApplication app = new SpringApplication(
                com.stravamirror.StravaActivityMirrorApplication.class);

        assertThatThrownBy(() -> app.run(
                "--strava.access-token=some-token",
                "--spring.flyway.enabled=false",
                "--spring.jpa.hibernate.ddl-auto=none"
        )).isNotNull();
    }
}
