package com.stravamirror.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ListIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM activity_summaries");
        jdbcTemplate.update(
                "INSERT INTO activity_summaries (activity_id, start_date, summary_json) VALUES "
                        + "(1, '2024-01-10T10:00:00Z', '{\"id\":1,\"name\":\"A1\"}'::jsonb),"
                        + "(2, '2024-01-15T10:00:00Z', '{\"id\":2,\"name\":\"A2\"}'::jsonb),"
                        + "(3, '2024-01-20T10:00:00Z', '{\"id\":3,\"name\":\"A3\"}'::jsonb),"
                        + "(4, '2024-01-20T10:00:00Z', '{\"id\":4,\"name\":\"A4\"}'::jsonb),"
                        + "(5, '2024-01-25T10:00:00Z', '{\"id\":5,\"name\":\"A5\"}'::jsonb)"
        );
    }

    @Test
    @DisplayName("AC-2.1 FR-008: list 5 activities in range returns all 5")
    void listAllActivities() throws Exception {
        mockMvc.perform(get("/athlete/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities.length()").value(5))
                .andExpect(jsonPath("$.total_count").value(5));
    }

    @Test
    @DisplayName("AC-2.2 FR-009: results returned newest-first")
    void newestFirst() throws Exception {
        mockMvc.perform(get("/athlete/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities[0].activity_id").value(5))
                .andExpect(jsonPath("$.activities[4].activity_id").value(1));
    }

    @Test
    @DisplayName("AC-2.3 FR-009: equal start_dates tiebreaker by descending activity_id")
    void tiebreaker() throws Exception {
        mockMvc.perform(get("/athlete/activities")
                        .param("after", String.valueOf(
                                java.time.Instant.parse("2024-01-19T00:00:00Z").getEpochSecond()))
                        .param("before", String.valueOf(
                                java.time.Instant.parse("2024-01-21T00:00:00Z").getEpochSecond())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities[0].activity_id").value(4))
                .andExpect(jsonPath("$.activities[1].activity_id").value(3));
    }

    @Test
    @DisplayName("AC-2.4 FR-010: page 2 with per_page=2 returns correct activities")
    void page2() throws Exception {
        mockMvc.perform(get("/athlete/activities")
                        .param("page", "2")
                        .param("per_page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities.length()").value(2))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.per_page").value(2));
    }

    @Test
    @DisplayName("AC-2.5 FR-010: default per_page=30")
    void defaultPerPage() throws Exception {
        mockMvc.perform(get("/athlete/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.per_page").value(30));
    }

    @Test
    @DisplayName("AC-2.6 FR-011: empty date range returns 200 with empty array")
    void emptyRange() throws Exception {
        mockMvc.perform(get("/athlete/activities")
                        .param("after", String.valueOf(
                                java.time.Instant.parse("2025-01-01T00:00:00Z").getEpochSecond()))
                        .param("before", String.valueOf(
                                java.time.Instant.parse("2025-02-01T00:00:00Z").getEpochSecond())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities").isEmpty())
                .andExpect(jsonPath("$.total_count").value(0));
    }

    @Test
    @DisplayName("AC-2.7 FR-011: empty DB returns 200 with empty array")
    void emptyDb() throws Exception {
        jdbcTemplate.execute("DELETE FROM activity_summaries");

        mockMvc.perform(get("/athlete/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities").isEmpty())
                .andExpect(jsonPath("$.total_count").value(0));
    }

    @Test
    @DisplayName("AC-2.11: exclusive boundary — activity at exactly 'after' is excluded")
    void exclusiveAfterBoundary() throws Exception {
        long afterEpoch = java.time.Instant.parse("2024-01-10T10:00:00Z").getEpochSecond();
        mockMvc.perform(get("/athlete/activities")
                        .param("after", String.valueOf(afterEpoch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities[?(@.activity_id == 1)]").doesNotExist());
    }

    @Test
    @DisplayName("AC-2.12: exclusive boundary — activity at exactly 'before' is excluded")
    void exclusiveBeforeBoundary() throws Exception {
        long beforeEpoch = java.time.Instant.parse("2024-01-25T10:00:00Z").getEpochSecond();
        mockMvc.perform(get("/athlete/activities")
                        .param("before", String.valueOf(beforeEpoch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities[?(@.activity_id == 5)]").doesNotExist());
    }

    @Test
    @DisplayName("AC-2.13 FR-013: pagination metadata has_next=true when more pages exist")
    void hasNextTrue() throws Exception {
        mockMvc.perform(get("/athlete/activities")
                        .param("per_page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.has_next").value(true));
    }

    @Test
    @DisplayName("AC-2.14 FR-013: last page has_next=false")
    void hasNextFalse() throws Exception {
        mockMvc.perform(get("/athlete/activities")
                        .param("page", "3")
                        .param("per_page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.has_next").value(false));
    }

    @Test
    @DisplayName("AC-2.15: empty DB pagination metadata — total_count=0, has_next=false")
    void emptyDbMetadata() throws Exception {
        jdbcTemplate.execute("DELETE FROM activity_summaries");

        mockMvc.perform(get("/athlete/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_count").value(0))
                .andExpect(jsonPath("$.has_next").value(false));
    }
}
