package com.linkplatform.api.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.linkplatform.api.link.application.PipelineControl;
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
class PipelineHealthIndicatorParityIntegrationTest {

    private static final String FREE_API_KEY = "free-owner-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PipelineControlStore pipelineControlStore;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void actuatorHealthMatchesPersistedPipelineControlAndOperatorStatus() throws Exception {
        jdbcTemplate.update(
                """
                INSERT INTO analytics_outbox (event_id, event_type, event_key, payload_json, created_at, parked_at)
                VALUES ('analytics-eligible', 'redirect-click', 'analytics-eligible', '{}', ?, NULL),
                       ('analytics-parked', 'redirect-click', 'analytics-parked', '{}', ?, ?)
                """,
                OffsetDateTime.parse("2026-04-06T07:00:00Z"),
                OffsetDateTime.parse("2026-04-06T07:05:00Z"),
                OffsetDateTime.parse("2026-04-06T07:06:00Z"));
        jdbcTemplate.update(
                """
                INSERT INTO link_lifecycle_outbox (event_id, event_type, event_key, payload_json, created_at, parked_at)
                VALUES ('lifecycle-eligible', 'UPDATED', 'lifecycle-eligible', '{}', ?, NULL),
                       ('lifecycle-parked', 'UPDATED', 'lifecycle-parked', '{}', ?, ?)
                """,
                OffsetDateTime.parse("2026-04-06T07:10:00Z"),
                OffsetDateTime.parse("2026-04-06T07:15:00Z"),
                OffsetDateTime.parse("2026-04-06T07:16:00Z"));

        String longFailureReason = "x".repeat(PipelineControl.MAX_FAILURE_REASON_LENGTH + 25);
        pipelineControlStore.pause("analytics", "maintenance window", OffsetDateTime.parse("2026-04-06T08:00:00Z"));
        pipelineControlStore.recordRequeue("analytics", OffsetDateTime.parse("2026-04-06T08:01:00Z"));
        pipelineControlStore.recordForceTick("analytics", OffsetDateTime.parse("2026-04-06T08:02:00Z"));
        pipelineControlStore.recordRelayFailure("analytics", OffsetDateTime.parse("2026-04-06T08:03:00Z"), longFailureReason);
        pipelineControlStore.pause("lifecycle", "staging hold", OffsetDateTime.parse("2026-04-06T08:04:00Z"));
        pipelineControlStore.recordRequeue("lifecycle", OffsetDateTime.parse("2026-04-06T08:05:00Z"));
        pipelineControlStore.recordForceTick("lifecycle", OffsetDateTime.parse("2026-04-06T08:06:00Z"));
        pipelineControlStore.recordRelaySuccess("lifecycle", OffsetDateTime.parse("2026-04-06T08:07:00Z"));

        JsonNode health = readJson(mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        JsonNode analyticsStatus = readJson(mockMvc.perform(get("/api/v1/analytics/pipeline").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        JsonNode lifecycleStatus = readJson(mockMvc.perform(get("/api/v1/lifecycle/pipeline").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        PipelineControl analyticsControl = pipelineControlStore.get("analytics");
        PipelineControl lifecycleControl = pipelineControlStore.get("lifecycle");

        JsonNode analyticsHealth = health.at("/components/pipeline/details/analytics");
        JsonNode lifecycleHealth = health.at("/components/pipeline/details/lifecycle");

        assertThat(analyticsHealth.path("pauseReason").asText()).isEqualTo(analyticsControl.pauseReason());
        assertTimestampEquals(analyticsHealth, "lastRequeueAt", analyticsControl.lastRequeueAt());
        assertTimestampEquals(analyticsHealth, "lastForceTickAt", analyticsControl.lastForceTickAt());
        assertThat(analyticsHealth.path("lastRelayFailureReason").asText()).isEqualTo(analyticsControl.lastRelayFailureReason());
        assertThat(analyticsHealth.path("lastRelayFailureReason").asText().length())
                .isEqualTo(PipelineControl.MAX_FAILURE_REASON_LENGTH);
        assertThat(analyticsHealth.path("eligibleCount").asLong()).isEqualTo(1L);
        assertThat(analyticsHealth.path("parkedCount").asLong()).isEqualTo(1L);
        assertThat(analyticsHealth.path("oldestEligibleAgeSeconds").isNumber()).isTrue();
        assertThat(analyticsHealth.path("oldestParkedAgeSeconds").isNumber()).isTrue();
        assertThat(analyticsHealth.path("pauseReason").asText()).isEqualTo(analyticsStatus.path("pauseReason").asText());
        assertThat(analyticsHealth.path("lastRequeueAt").asText()).isEqualTo(analyticsStatus.path("lastRequeueAt").asText());
        assertThat(analyticsHealth.path("lastForceTickAt").asText()).isEqualTo(analyticsStatus.path("lastForceTickAt").asText());
        assertThat(analyticsHealth.path("lastRelayFailureReason").asText())
                .isEqualTo(analyticsStatus.path("lastRelayFailureReason").asText());

        assertThat(lifecycleHealth.path("pauseReason").asText()).isEqualTo(lifecycleControl.pauseReason());
        assertTimestampEquals(lifecycleHealth, "lastRequeueAt", lifecycleControl.lastRequeueAt());
        assertTimestampEquals(lifecycleHealth, "lastForceTickAt", lifecycleControl.lastForceTickAt());
        assertTimestampEquals(lifecycleHealth, "lastRelaySuccessAt", lifecycleControl.lastRelaySuccessAt());
        assertThat(lifecycleHealth.path("eligibleCount").asLong()).isEqualTo(1L);
        assertThat(lifecycleHealth.path("parkedCount").asLong()).isEqualTo(1L);
        assertThat(lifecycleHealth.path("pauseReason").asText()).isEqualTo(lifecycleStatus.path("pauseReason").asText());
        assertThat(lifecycleHealth.path("lastRequeueAt").asText()).isEqualTo(lifecycleStatus.path("lastRequeueAt").asText());
        assertThat(lifecycleHealth.path("lastForceTickAt").asText()).isEqualTo(lifecycleStatus.path("lastForceTickAt").asText());
        assertThat(lifecycleHealth.path("lastRelaySuccessAt").asText())
                .isEqualTo(lifecycleStatus.path("lastRelaySuccessAt").asText());
        assertThat(lifecycleHealth.path("lastRelayFailureReason").isMissingNode()
                        || lifecycleHealth.path("lastRelayFailureReason").isNull())
                .isTrue();
    }

    private JsonNode readJson(String content) throws Exception {
        return jsonMapper.readTree(content);
    }

    private void assertTimestampEquals(JsonNode node, String fieldName, OffsetDateTime expected) {
        assertThat(OffsetDateTime.parse(node.path(fieldName).asText())).isEqualTo(expected);
    }
}
