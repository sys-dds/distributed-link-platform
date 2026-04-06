package com.linkplatform.api.link.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "link-platform.analytics.outbox-relay-batch-size=1"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AnalyticsOutboxRelayIntegrationTest {

    @Autowired
    private AnalyticsOutboxRelay relay;

    @Autowired
    private PipelineControlStore pipelineControlStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void pausedPipelineDoesNotClaimEligibleRowsAndResumedPipelineProcessesRowsAgain() {
        insertEligible("analytics-a", OffsetDateTime.parse("2026-04-06T08:00:00Z"));
        pipelineControlStore.pause("analytics", "maintenance", OffsetDateTime.parse("2026-04-06T08:30:00Z"));

        AnalyticsOutboxRelay.RelayIterationResult paused = relay.relayOnce();
        assertEquals(0, paused.processedCount());
        assertEquals(1, countEligible());
        assertNull(findClaimedBy("analytics-a"));

        pipelineControlStore.resume("analytics", OffsetDateTime.parse("2026-04-06T08:31:00Z"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        AnalyticsOutboxRelay.RelayIterationResult resumed = relay.relayOnce();
        assertEquals(1, resumed.processedCount());
        assertNotNull(findPublishedAt("analytics-a"));
        assertEquals(0, countEligible());
    }

    @Test
    void forceTickProcessesExactlyOneIteration() {
        insertEligible("analytics-b", OffsetDateTime.parse("2026-04-06T08:00:00Z"));
        insertEligible("analytics-c", OffsetDateTime.parse("2026-04-06T08:01:00Z"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        AnalyticsOutboxRelay.RelayIterationResult result = relay.relayOnce();

        assertEquals(1, result.processedCount());
        assertEquals(1, countPublished());
        assertEquals(1, countEligible());
    }

    @Test
    void failureRecordsLastRelayFailureTimestampAndSafeReason() {
        insertEligible("analytics-fail", OffsetDateTime.parse("2026-04-06T08:00:00Z"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("x".repeat(800)));

        assertThrows(RuntimeException.class, () -> relay.relayOnce());

        PipelineControl control = pipelineControlStore.get("analytics");
        assertNotNull(control.lastRelayFailureAt());
        assertNotNull(control.lastRelayFailureReason());
        assertTrue(control.lastRelayFailureReason().length() <= 512);
    }

    private void insertEligible(String eventId, OffsetDateTime createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO analytics_outbox (event_id, event_type, event_key, payload_json, created_at)
                VALUES (?, 'redirect-click', ?, '{}', ?)
                """,
                eventId,
                eventId,
                createdAt);
    }

    private int countEligible() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM analytics_outbox WHERE published_at IS NULL AND parked_at IS NULL",
                Integer.class);
        return count == null ? 0 : count;
    }

    private int countPublished() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM analytics_outbox WHERE published_at IS NOT NULL",
                Integer.class);
        return count == null ? 0 : count;
    }

    private String findClaimedBy(String eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT claimed_by FROM analytics_outbox WHERE event_id = ?",
                String.class,
                eventId);
    }

    private OffsetDateTime findPublishedAt(String eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT published_at FROM analytics_outbox WHERE event_id = ?",
                OffsetDateTime.class,
                eventId);
    }
}
