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
class LifecyclePipelineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void statusShowsLifecycleEligibleBacklogAndParkedCounts() throws Exception {
        insertOutboxRow("event-1", "CREATED", "launch-page", OffsetDateTime.now().minusMinutes(5), 0, null, null, null);
        insertOutboxRow("event-2", "DELETED", "gone-link", OffsetDateTime.now().minusMinutes(10), 5, null,
                "RuntimeException: Permanent failure", OffsetDateTime.now().minusMinutes(1));

        mockMvc.perform(get("/api/v1/lifecycle/pipeline"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.eligibleBacklogCount").value(1))
                .andExpect(jsonPath("$.parkedCount").value(1))
                .andExpect(jsonPath("$.oldestEligibleAgeSeconds").isNumber());
    }

    @Test
    void parkedEndpointListsAndRequeuesLifecycleRows() throws Exception {
        long parkedId = insertOutboxRow(
                "event-3",
                "DELETED",
                "gone-link",
                OffsetDateTime.now().minusMinutes(20),
                5,
                null,
                "RuntimeException: Permanent failure",
                OffsetDateTime.now().minusMinutes(2));

        mockMvc.perform(get("/api/v1/lifecycle/pipeline/parked"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(parkedId))
                .andExpect(jsonPath("$[0].eventType").value("DELETED"))
                .andExpect(jsonPath("$[0].eventKey").value("gone-link"));

        mockMvc.perform(post("/api/v1/lifecycle/pipeline/parked/{id}/requeue", parkedId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/lifecycle/pipeline/parked"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private long insertOutboxRow(
            String eventId,
            String eventType,
            String eventKey,
            OffsetDateTime createdAt,
            Integer attemptCount,
            OffsetDateTime nextAttemptAt,
            String lastErrorSummary,
            OffsetDateTime parkedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO link_lifecycle_outbox (
                    event_id, event_type, event_key, payload_json, created_at,
                    attempt_count, next_attempt_at, last_error_summary, parked_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                eventId,
                eventType,
                eventKey,
                "{\"eventId\":\"%s\"}".formatted(eventId),
                createdAt,
                attemptCount,
                nextAttemptAt,
                lastErrorSummary,
                parkedAt);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM link_lifecycle_outbox WHERE event_id = ?",
                Long.class,
                eventId);
    }
}
