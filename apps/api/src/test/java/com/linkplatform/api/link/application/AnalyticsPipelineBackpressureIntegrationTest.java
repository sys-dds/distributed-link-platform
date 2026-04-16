package com.linkplatform.api.link.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AnalyticsPipelineBackpressureIntegrationTest {

    @Autowired
    private AnalyticsOutboxStore analyticsOutboxStore;

    @Autowired
    private PipelineControlStore pipelineControlStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void slowProcessingLeavesBoundedBacklogVisibleWithoutBreakingCorrectness() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.<SendResult<String, String>>completedFuture(null));
        AnalyticsOutboxRelay relay = new AnalyticsOutboxRelay(
                analyticsOutboxStore,
                pipelineControlStore,
                kafkaTemplate,
                new SimpleMeterRegistry(),
                "analytics-clicks",
                1,
                Duration.ofMinutes(1),
                Duration.ZERO,
                Duration.ZERO,
                3,
                Clock.systemUTC(),
                "slow-worker");
        insertOutboxRecord("slow-event-1", "slow-key-1", OffsetDateTime.now().minusMinutes(10));
        insertOutboxRecord("slow-event-2", "slow-key-2", OffsetDateTime.now().minusMinutes(9));
        insertOutboxRecord("slow-event-3", "slow-key-3", OffsetDateTime.now().minusMinutes(8));

        AnalyticsOutboxRelay.RelayIterationResult result = relay.relayOnce();

        assertEquals(1, result.processedCount());
        assertEquals(0, result.parkedCount());
        assertEquals(1L, count("SELECT COUNT(*) FROM analytics_outbox WHERE published_at IS NOT NULL"));
        assertEquals(2L, count("SELECT COUNT(*) FROM analytics_outbox WHERE published_at IS NULL AND parked_at IS NULL"));
        assertEquals(2L, analyticsOutboxStore.countEligible(OffsetDateTime.now()));
        assertEquals(0L, analyticsOutboxStore.countParked());

        mockMvc.perform(get("/api/v1/analytics/pipeline")
                        .header("X-API-Key", bootstrapOpsReadKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipelineName").value("analytics"))
                .andExpect(jsonPath("$.eligibleCount").value(2))
                .andExpect(jsonPath("$.parkedCount").value(0))
                .andExpect(jsonPath("$.oldestEligibleAgeSeconds").isNumber())
                .andExpect(jsonPath("$.oldestEligibleAgeSeconds").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0.0)));
    }

    private void insertOutboxRecord(String eventId, String eventKey, OffsetDateTime createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO analytics_outbox (event_id, event_type, event_key, payload_json, created_at)
                VALUES (?, 'redirect-click', ?, '{}', ?)
                """,
                eventId,
                eventKey,
                createdAt);
    }

    private String bootstrapOpsReadKey() {
        long workspaceId = jdbcTemplate.queryForObject(
                "SELECT id FROM workspaces WHERE created_by_owner_id = 1 AND personal_workspace = TRUE LIMIT 1",
                Long.class);
        String plaintextKey = "backpressure-key";
        jdbcTemplate.update(
                """
                INSERT INTO owner_api_keys (owner_id, workspace_id, key_prefix, key_hash, key_label, label, scopes_json, created_at, created_by)
                VALUES (1, ?, ?, ?, ?, ?, CAST('[\"ops:read\"]' AS jsonb), ?, 'backpressure-test')
                """,
                workspaceId,
                plaintextKey,
                sha256(plaintextKey),
                plaintextKey,
                plaintextKey,
                OffsetDateTime.now());
        return plaintextKey;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte part : hash) {
                hex.append(String.format("%02x", part));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private long count(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }
}
