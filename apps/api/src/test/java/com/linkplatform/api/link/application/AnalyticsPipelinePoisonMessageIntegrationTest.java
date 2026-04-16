package com.linkplatform.api.link.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AnalyticsPipelinePoisonMessageIntegrationTest {

    @Autowired
    private AnalyticsOutboxStore analyticsOutboxStore;

    @Autowired
    private PipelineControlStore pipelineControlStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void poisonMessageParksAfterBoundedRetriesAndCanBeRequeuedPredictably() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.<SendResult<String, String>>failedFuture(
                        new IllegalStateException("poison-payload")));
        AnalyticsOutboxRelay relay = new AnalyticsOutboxRelay(
                analyticsOutboxStore,
                pipelineControlStore,
                kafkaTemplate,
                new SimpleMeterRegistry(),
                "analytics-clicks",
                10,
                Duration.ofMinutes(1),
                Duration.ZERO,
                Duration.ZERO,
                2,
                Clock.systemUTC(),
                "poison-worker");
        long id = insertOutboxRecord("poison-event-1", "poison-key");

        assertThrows(CompletionException.class, relay::relayOnce);
        assertEquals(0L, analyticsOutboxStore.countParked());
        assertThrows(CompletionException.class, relay::relayOnce);

        List<AnalyticsOutboxRecord> parked = analyticsOutboxStore.findParked(10);
        assertEquals(1, parked.size());
        AnalyticsOutboxRecord parkedRecord = parked.getFirst();
        assertEquals(id, parkedRecord.id());
        assertEquals(2, parkedRecord.attemptCount());
        assertNotNull(parkedRecord.parkedAt());
        assertNotNull(parkedRecord.lastErrorSummary());
        assertTrue(parkedRecord.lastErrorSummary().contains("IllegalStateException: poison-payload"));
        assertNotNull(pipelineControlStore.get(AnalyticsOutboxRelay.PIPELINE_NAME).lastRelayFailureReason());
        assertTrue(pipelineControlStore.get(AnalyticsOutboxRelay.PIPELINE_NAME)
                .lastRelayFailureReason()
                .contains("IllegalStateException: poison-payload"));
        assertEquals(1L, analyticsOutboxStore.countParked());
        assertEquals(0L, analyticsOutboxStore.countEligible(OffsetDateTime.now()));

        assertTrue(analyticsOutboxStore.requeueParked(id, OffsetDateTime.now().minusSeconds(1)));

        AnalyticsOutboxRecord requeued = analyticsOutboxStore.claimBatch(
                        "recovery-worker",
                        OffsetDateTime.now(),
                        OffsetDateTime.now().plusMinutes(1),
                        10)
                .getFirst();
        assertEquals(id, requeued.id());
        assertEquals(2, requeued.attemptCount());
        assertEquals("recovery-worker", requeued.claimedBy());
        assertEquals("recovery-worker", jdbcTemplate.queryForObject(
                "SELECT claimed_by FROM analytics_outbox WHERE id = ?",
                String.class,
                id));
        assertEquals(0L, analyticsOutboxStore.countParked());
        assertEquals(0L, count("SELECT COUNT(*) FROM analytics_outbox WHERE id = ? AND parked_at IS NOT NULL", id));
    }

    private long insertOutboxRecord(String eventId, String eventKey) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO analytics_outbox (event_id, event_type, event_key, payload_json)
                VALUES (?, 'redirect-click', ?, '{}')
                RETURNING id
                """,
                Long.class,
                eventId,
                eventKey);
    }

    private long count(String sql, Object... parameters) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, parameters);
        return count == null ? 0L : count;
    }
}
