package com.stravamirror.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.stravamirror.entity.ActivitySummary;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ActivitySummaryRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private ActivitySummaryRepository repository;

    @Test
    @DisplayName("FR-003: inserting 3 new activities returns inserted=3")
    void insertThreeNewActivitiesReturnsThree() {
        int inserted = repository.bulkInsertOnConflictDoNothing(List.of(
                new ActivitySummary(1L, Instant.parse("2024-01-01T10:00:00Z"), "{\"id\":1}"),
                new ActivitySummary(2L, Instant.parse("2024-01-02T10:00:00Z"), "{\"id\":2}"),
                new ActivitySummary(3L, Instant.parse("2024-01-03T10:00:00Z"), "{\"id\":3}")
        ));

        assertThat(inserted).isEqualTo(3);
        assertThat(repository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("FR-004: re-inserting same 3 activities returns inserted=0")
    void reInsertSameThreeReturnsZero() {
        List<ActivitySummary> activities = List.of(
                new ActivitySummary(10L, Instant.parse("2024-01-01T10:00:00Z"), "{\"id\":10}"),
                new ActivitySummary(11L, Instant.parse("2024-01-02T10:00:00Z"), "{\"id\":11}"),
                new ActivitySummary(12L, Instant.parse("2024-01-03T10:00:00Z"), "{\"id\":12}")
        );
        repository.bulkInsertOnConflictDoNothing(activities);

        int inserted = repository.bulkInsertOnConflictDoNothing(activities);
        assertThat(inserted).isEqualTo(0);
    }

    @Test
    @DisplayName("FR-004: inserting 5 where 2 exist returns inserted=3")
    void insertFiveWithTwoExistingReturnsThree() {
        repository.bulkInsertOnConflictDoNothing(List.of(
                new ActivitySummary(20L, Instant.parse("2024-01-01T10:00:00Z"), "{\"id\":20}"),
                new ActivitySummary(21L, Instant.parse("2024-01-02T10:00:00Z"), "{\"id\":21}")
        ));

        int inserted = repository.bulkInsertOnConflictDoNothing(List.of(
                new ActivitySummary(20L, Instant.parse("2024-01-01T10:00:00Z"), "{\"id\":20}"),
                new ActivitySummary(21L, Instant.parse("2024-01-02T10:00:00Z"), "{\"id\":21}"),
                new ActivitySummary(22L, Instant.parse("2024-01-03T10:00:00Z"), "{\"id\":22}"),
                new ActivitySummary(23L, Instant.parse("2024-01-04T10:00:00Z"), "{\"id\":23}"),
                new ActivitySummary(24L, Instant.parse("2024-01-05T10:00:00Z"), "{\"id\":24}")
        ));

        assertThat(inserted).isEqualTo(3);
    }

    @Test
    @DisplayName("FR-003: start_date stored as UTC")
    void startDateStoredAsUtc() {
        Instant utcTime = Instant.parse("2024-06-15T08:30:00Z");
        repository.bulkInsertOnConflictDoNothing(List.of(
                new ActivitySummary(30L, utcTime, "{\"id\":30}")
        ));

        ActivitySummary found = repository.findById(30L).orElseThrow();
        assertThat(found.getStartDate()).isEqualTo(utcTime);
    }
}
