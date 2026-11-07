package com.stravamirror.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.stravamirror.entity.ActivityDetail;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ActivityDetailRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private ActivityDetailRepository repository;

    @Test
    @DisplayName("FR-015: save and retrieve detail record with JSONB storage")
    void saveAndRetrieveDetail() {
        String json = "{\"id\":123,\"name\":\"Morning Run\",\"distance\":5000}";
        Instant startDate = Instant.parse("2024-06-15T08:30:00Z");
        ActivityDetail detail = new ActivityDetail(123L, startDate, json);

        repository.save(detail);

        Optional<ActivityDetail> found = repository.findById(123L);
        assertThat(found).isPresent();
        assertThat(found.get().getActivityId()).isEqualTo(123L);
        assertThat(found.get().getDetailJson()).isEqualTo(json);
    }

    @Test
    @DisplayName("FR-015: start_date stored as UTC")
    void startDateStoredAsUtc() {
        Instant utcTime = Instant.parse("2024-03-20T14:00:00Z");
        repository.save(new ActivityDetail(456L, utcTime, "{\"id\":456}"));

        ActivityDetail found = repository.findById(456L).orElseThrow();
        assertThat(found.getStartDate()).isEqualTo(utcTime);
    }

    @Test
    @DisplayName("FR-015: findById returns empty for non-existent ID")
    void findByIdReturnsEmptyForMissing() {
        Optional<ActivityDetail> found = repository.findById(999L);
        assertThat(found).isEmpty();
    }
}
