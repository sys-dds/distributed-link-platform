package com.linkplatform.api.link.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AnalyticsPipelineControllerTest {

    private static final String FREE_API_KEY = "free-owner-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void statusShowsEligibleBacklogAndParkedCounts() throws Exception {
        insertOutboxRow("event-1", "alpha-link", OffsetDateTime.now().minusMinutes(5), null, null, null, null);
        insertOutboxRow(
                "event-2",
                "beta-link",
                OffsetDateTime.now().minusMinutes(10),
                5,
                null,
                "RuntimeException: Permanent failure",
                OffsetDateTime.now().minusMinutes(1));

        mockMvc.perform(get("/api/v1/analytics/pipeline").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.eligibleBacklogCount").value(1))
                .andExpect(jsonPath("$.parkedCount").value(1))
                .andExpect(jsonPath("$.oldestEligibleAgeSeconds").isNumber());
    }

    @Test
    void parkedEndpointListsParkedRowsAndSupportsRequeue() throws Exception {
        long parkedId = insertOutboxRow(
                "event-3",
                "gamma-link",
                OffsetDateTime.now().minusMinutes(20),
                5,
                null,
                "RuntimeException: Permanent failure",
                OffsetDateTime.now().minusMinutes(2));

        mockMvc.perform(get("/api/v1/analytics/pipeline/parked").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(parkedId))
                .andExpect(jsonPath("$[0].eventId").value("event-3"))
                .andExpect(jsonPath("$[0].eventKey").value("gamma-link"))
                .andExpect(jsonPath("$[0].attemptCount").value(5))
                .andExpect(jsonPath("$[0].lastErrorSummary").value("RuntimeException: Permanent failure"));

        mockMvc.perform(post("/api/v1/analytics/pipeline/parked/{id}/requeue", parkedId)
                        .header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/analytics/pipeline/parked").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void pipelineEndpointsRequireApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/pipeline"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("X-API-Key header is required"));

        mockMvc.perform(post("/api/v1/analytics/pipeline/parked/999/requeue"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("X-API-Key header is required"));
    }

    @Test
    void requeueReturnsNotFoundForMissingParkedRow() throws Exception {
        mockMvc.perform(post("/api/v1/analytics/pipeline/parked/999/requeue")
                        .header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isNotFound());
    }

    private long insertOutboxRow(
            String eventId,
            String eventKey,
            OffsetDateTime createdAt,
            Integer attemptCount,
            OffsetDateTime nextAttemptAt,
            String lastErrorSummary,
            OffsetDateTime parkedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO analytics_outbox (
                    event_id, event_type, event_key, payload_json, created_at,
                    attempt_count, next_attempt_at, last_error_summary, parked_at
                )
                VALUES (?, 'redirect-click', ?, ?, ?, ?, ?, ?, ?)
                """,
                eventId,
                eventKey,
                "{\"eventId\":\"%s\"}".formatted(eventId),
                createdAt,
                attemptCount == null ? 0 : attemptCount,
                nextAttemptAt,
                lastErrorSummary,
                parkedAt);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM analytics_outbox WHERE event_id = ?",
                Long.class,
                eventId);
    }
}
