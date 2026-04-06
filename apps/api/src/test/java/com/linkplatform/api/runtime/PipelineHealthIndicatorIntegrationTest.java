package com.linkplatform.api.runtime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.linkplatform.api.link.application.PipelineControlStore;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "management.endpoint.health.show-details=always",
        "management.endpoint.health.group.readiness.show-details=always",
        "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PipelineHealthIndicatorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PipelineControlStore pipelineControlStore;

    @Test
    void exposesPausedBacklogAndRelayTimestampsForBothPipelines() throws Exception {
        jdbcTemplate.update(
                """
                INSERT INTO analytics_outbox (event_id, event_type, event_key, payload_json, created_at, parked_at)
                VALUES ('analytics-health', 'redirect-click', 'analytics-health', '{}', ?, ?)
                """,
                OffsetDateTime.parse("2026-04-06T08:00:00Z"),
                OffsetDateTime.parse("2026-04-06T08:05:00Z"));
        jdbcTemplate.update(
                """
                INSERT INTO link_lifecycle_outbox (event_id, event_type, event_key, payload_json, created_at)
                VALUES ('lifecycle-health', 'UPDATED', 'lifecycle-health', '{}', ?)
                """,
                OffsetDateTime.parse("2026-04-06T08:10:00Z"));

        pipelineControlStore.pause("analytics", "maintenance", OffsetDateTime.parse("2026-04-06T08:20:00Z"));
        pipelineControlStore.recordRelayFailure("analytics", OffsetDateTime.parse("2026-04-06T08:21:00Z"), "failure");
        pipelineControlStore.recordRelaySuccess("lifecycle", OffsetDateTime.parse("2026-04-06T08:22:00Z"));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.pipeline.details.analytics.paused").value(true))
                .andExpect(jsonPath("$.components.pipeline.details.analytics.parkedCount").value(1))
                .andExpect(jsonPath("$.components.pipeline.details.analytics.lastRelayFailureAt").isNotEmpty())
                .andExpect(jsonPath("$.components.pipeline.details.lifecycle.paused").value(false))
                .andExpect(jsonPath("$.components.pipeline.details.lifecycle.eligibleCount").value(1))
                .andExpect(jsonPath("$.components.pipeline.details.lifecycle.lastRelaySuccessAt").isNotEmpty());
    }
}
