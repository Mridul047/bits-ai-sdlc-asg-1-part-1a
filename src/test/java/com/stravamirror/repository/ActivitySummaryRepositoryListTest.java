package com.stravamirror.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.stravamirror.entity.ActivitySummary;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ActivitySummaryRepositoryListTest {

    private static final Instant MIN_DATE = Instant.EPOCH;
    private static final Instant MAX_DATE = Instant.parse("9999-12-31T23:59:59Z");

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
    private ActivitySummaryRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.bulkInsertOnConflictDoNothing(List.of(
                new ActivitySummary(1L, Instant.parse("2024-01-10T10:00:00Z"), "{\"id\":1}"),
                new ActivitySummary(2L, Instant.parse("2024-01-15T10:00:00Z"), "{\"id\":2}"),
                new ActivitySummary(3L, Instant.parse("2024-01-20T10:00:00Z"), "{\"id\":3}"),
                new ActivitySummary(4L, Instant.parse("2024-01-20T10:00:00Z"), "{\"id\":4}"),
                new ActivitySummary(5L, Instant.parse("2024-01-25T10:00:00Z"), "{\"id\":5}")
        ));
    }

    @Test
    @DisplayName("FR-009: results returned newest-first by start_date")
    void newestFirst() {
        Page<ActivitySummary> page = repository.findByDateRange(MIN_DATE, MAX_DATE, PageRequest.of(0, 10));
        List<ActivitySummary> content = page.getContent();

        assertThat(content).extracting(ActivitySummary::getStartDate)
                .isSortedAccordingTo((a, b) -> b.compareTo(a));
    }

    @Test
    @DisplayName("FR-009: equal start_dates broken by descending activity_id")
    void tiebreaker() {
        Instant jan20 = Instant.parse("2024-01-20T10:00:00Z");
        Page<ActivitySummary> page = repository.findByDateRange(
                Instant.parse("2024-01-19T00:00:00Z"),
                Instant.parse("2024-01-21T00:00:00Z"),
                PageRequest.of(0, 10));

        List<ActivitySummary> content = page.getContent();
        assertThat(content).hasSize(2);
        assertThat(content.get(0).getActivityId()).isEqualTo(4L);
        assertThat(content.get(1).getActivityId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("FR-010: exclusive boundary filtering — after boundary excluded")
    void exclusiveAfterBoundary() {
        Instant afterBoundary = Instant.parse("2024-01-10T10:00:00Z");
        Page<ActivitySummary> page = repository.findByDateRange(
                afterBoundary, MAX_DATE, PageRequest.of(0, 10));

        assertThat(page.getContent())
                .extracting(ActivitySummary::getActivityId)
                .doesNotContain(1L);
    }

    @Test
    @DisplayName("FR-010: exclusive boundary filtering — before boundary excluded")
    void exclusiveBeforeBoundary() {
        Instant beforeBoundary = Instant.parse("2024-01-25T10:00:00Z");
        Page<ActivitySummary> page = repository.findByDateRange(
                MIN_DATE, beforeBoundary, PageRequest.of(0, 10));

        assertThat(page.getContent())
                .extracting(ActivitySummary::getActivityId)
                .doesNotContain(5L);
    }

    @Test
    @DisplayName("FR-010: pagination via Spring Data Page")
    void pagination() {
        Page<ActivitySummary> page1 = repository.findByDateRange(MIN_DATE, MAX_DATE, PageRequest.of(0, 2));
        assertThat(page1.getContent()).hasSize(2);
        assertThat(page1.getTotalElements()).isEqualTo(5);
        assertThat(page1.hasNext()).isTrue();

        Page<ActivitySummary> page3 = repository.findByDateRange(MIN_DATE, MAX_DATE, PageRequest.of(2, 2));
        assertThat(page3.getContent()).hasSize(1);
        assertThat(page3.hasNext()).isFalse();
    }
}
