package com.linkplatform.api.link.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class LifecyclePipelineControllerIntegrationTest {

    private static final String FREE_API_KEY = "free-owner-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void parkedBatchRequeueAndRequeueAllUpdateStatus() throws Exception {
        long first = insertOutbox("lifecycle-a", OffsetDateTime.parse("2026-04-06T10:00:00Z"), OffsetDateTime.parse("2026-04-06T10:10:00Z"));
        insertOutbox("lifecycle-b", OffsetDateTime.parse("2026-04-06T09:00:00Z"), OffsetDateTime.parse("2026-04-06T09:10:00Z"));
        insertOutbox("lifecycle-c", OffsetDateTime.parse("2026-04-06T08:00:00Z"), null);

        mockMvc.perform(get("/api/v1/lifecycle/pipeline").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligibleCount").value(1))
                .andExpect(jsonPath("$.parkedCount").value(2))
                .andExpect(jsonPath("$.oldestEligibleAgeSeconds").isNumber())
                .andExpect(jsonPath("$.oldestParkedAgeSeconds").isNumber());

        mockMvc.perform(post("/api/v1/lifecycle/pipeline/parked/requeue-batch")
                        .header("X-API-Key", FREE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ids":[%d]}
                                """.formatted(first)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/lifecycle/pipeline/parked/requeue-all")
                        .header("X-API-Key", FREE_API_KEY)
                        .param("limit", "10"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/lifecycle/pipeline").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parkedCount").value(0));
    }

    private long insertOutbox(String eventId, OffsetDateTime createdAt, OffsetDateTime parkedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO link_lifecycle_outbox (
                    event_id, event_type, event_key, payload_json, created_at, parked_at, attempt_count
                ) VALUES (?, 'UPDATED', ?, '{}', ?, ?, 3)
                """,
                eventId,
                eventId,
                createdAt,
                parkedAt);
        return jdbcTemplate.queryForObject("SELECT id FROM link_lifecycle_outbox WHERE event_id = ?", Long.class, eventId);
    }
}
