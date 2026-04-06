package com.linkplatform.api.link.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class JdbcAnalyticsOutboxStoreTest {

    @Autowired
    private JdbcAnalyticsOutboxStore jdbcAnalyticsOutboxStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void saveRedirectClickEventPersistsStablePerLinkEventKey() {
        jdbcAnalyticsOutboxStore.saveRedirectClickEvent(new RedirectClickAnalyticsEvent(
                "event-1",
                "launch-page",
                OffsetDateTime.parse("2026-04-03T09:00:00Z"),
                "test-agent",
                "https://referrer.example",
                "127.0.0.1"));

        AnalyticsOutboxRecord outboxRecord = jdbcTemplate.queryForObject(
                """
                SELECT id, event_id, event_type, event_key, payload_json, created_at, published_at, claimed_by, claimed_until,
                       attempt_count, next_attempt_at, last_error_summary, parked_at
                FROM analytics_outbox
                WHERE event_id = ?
                """,
                (resultSet, rowNum) -> mapRecord(resultSet),
                "event-1");

        assertEquals("redirect-click", outboxRecord.eventType());
        assertEquals("launch-page", outboxRecord.eventKey());
        assertNull(outboxRecord.publishedAt());
    }

    @Test
    void unpublishedOutboxGaugeReflectsStoredBacklog() {
        jdbcAnalyticsOutboxStore.saveRedirectClickEvent(new RedirectClickAnalyticsEvent(
                "event-2",
                "launch-page",
                OffsetDateTime.parse("2026-04-03T09:05:00Z"),
                "test-agent",
                "https://referrer.example",
                "127.0.0.1"));
        jdbcAnalyticsOutboxStore.saveRedirectClickEvent(new RedirectClickAnalyticsEvent(
                "event-3",
                "docs-page",
                OffsetDateTime.parse("2026-04-03T09:06:00Z"),
                "test-agent",
                "https://referrer.example",
                "127.0.0.1"));

        assertEquals(2L, jdbcAnalyticsOutboxStore.countUnpublished());
        assertEquals(2.0, meterRegistry.get("link.analytics.outbox.unpublished").gauge().value());
        assertEquals(2.0, meterRegistry.get("link.analytics.outbox.eligible").gauge().value());
        assertEquals(0.0, meterRegistry.get("link.analytics.outbox.parked").gauge().value());
        assertTrue(meterRegistry.get("link.analytics.outbox.oldest.eligible.age.seconds").gauge().value() >= 0.0);
    }

    @Test
    void claimBatchAcquiresRowsInDeterministicOrder() {
        saveEvent("event-10", "alpha-link", OffsetDateTime.parse("2026-04-03T09:00:00Z"));
        saveEvent("event-11", "beta-link", OffsetDateTime.parse("2026-04-03T09:00:01Z"));

        List<AnalyticsOutboxRecord> claimed = jdbcAnalyticsOutboxStore.claimBatch(
                "worker-a",
                OffsetDateTime.parse("2026-04-03T09:00:00Z"),
                OffsetDateTime.parse("2026-04-03T09:05:00Z"),
                10);

        assertEquals(List.of("event-10", "event-11"), claimed.stream().map(AnalyticsOutboxRecord::eventId).toList());
        assertEquals(List.of("worker-a", "worker-a"), claimed.stream().map(AnalyticsOutboxRecord::claimedBy).toList());
    }

    @Test
    void concurrentWorkersDoNotClaimSameRows() throws Exception {
        saveEvent("event-20", "alpha-link", OffsetDateTime.parse("2026-04-03T09:00:00Z"));
        saveEvent("event-21", "beta-link", OffsetDateTime.parse("2026-04-03T09:00:01Z"));
        saveEvent("event-22", "gamma-link", OffsetDateTime.parse("2026-04-03T09:00:02Z"));
        saveEvent("event-23", "delta-link", OffsetDateTime.parse("2026-04-03T09:00:03Z"));

        List<AnalyticsOutboxRecord> firstClaim = jdbcAnalyticsOutboxStore.claimBatch(
                "worker-a",
                OffsetDateTime.parse("2026-04-03T09:00:10Z"),
                OffsetDateTime.parse("2026-04-03T09:05:00Z"),
                2);
        List<AnalyticsOutboxRecord> secondClaim = jdbcAnalyticsOutboxStore.claimBatch(
                "worker-b",
                OffsetDateTime.parse("2026-04-03T09:00:11Z"),
                OffsetDateTime.parse("2026-04-03T09:05:00Z"),
                2);

        Set<Long> claimedIds = ConcurrentHashMap.newKeySet();
        firstClaim.stream().map(AnalyticsOutboxRecord::id).forEach(claimedIds::add);
        secondClaim.stream().map(AnalyticsOutboxRecord::id).forEach(id -> assertTrue(claimedIds.add(id)));

        List<AnalyticsOutboxRecord> remainingClaim = jdbcAnalyticsOutboxStore.claimBatch(
                "worker-c",
                OffsetDateTime.parse("2026-04-03T09:00:12Z"),
                OffsetDateTime.parse("2026-04-03T09:05:00Z"),
                10);

        assertEquals(4, claimedIds.size());
        assertTrue(remainingClaim.isEmpty());
    }

    @Test
    void staleClaimsBecomeRecoverableAfterLeaseExpiry() {
        saveEvent("event-30", "alpha-link", OffsetDateTime.parse("2026-04-03T09:00:00Z"));

        List<AnalyticsOutboxRecord> firstClaim = jdbcAnalyticsOutboxStore.claimBatch(
                "worker-a",
                OffsetDateTime.parse("2026-04-03T09:00:10Z"),
                OffsetDateTime.parse("2026-04-03T09:01:00Z"),
                10);
        List<AnalyticsOutboxRecord> blockedClaim = jdbcAnalyticsOutboxStore.claimBatch(
                "worker-b",
                OffsetDateTime.parse("2026-04-03T09:00:30Z"),
                OffsetDateTime.parse("2026-04-03T09:00:30Z"),
                10);
        List<AnalyticsOutboxRecord> recoveredClaim = jdbcAnalyticsOutboxStore.claimBatch(
                "worker-b",
                OffsetDateTime.parse("2026-04-03T09:01:30Z"),
                OffsetDateTime.parse("2026-04-03T09:02:00Z"),
                10);

        assertEquals(1, firstClaim.size());
        assertEquals(0, blockedClaim.size());
        assertEquals(1, recoveredClaim.size());
        assertEquals("worker-b", recoveredClaim.getFirst().claimedBy());
    }

    @Test
    void markPublishedClearsClaimAndSetsPublishedTimestamp() {
        saveEvent("event-40", "alpha-link", OffsetDateTime.parse("2026-04-03T09:00:00Z"));

        AnalyticsOutboxRecord claimed = jdbcAnalyticsOutboxStore.claimBatch(
                "worker-a",
                OffsetDateTime.parse("2026-04-03T09:00:10Z"),
                OffsetDateTime.parse("2026-04-03T09:01:00Z"),
                10).getFirst();
        jdbcAnalyticsOutboxStore.markPublished(claimed.id(), OffsetDateTime.parse("2026-04-03T09:00:30Z"));

        AnalyticsOutboxRecord stored = findByEventId("event-40");
        assertEquals(OffsetDateTime.parse("2026-04-03T09:00:30Z"), stored.publishedAt());
        assertNull(stored.claimedBy());
        assertNull(stored.claimedUntil());
    }

    @Test
    void failedPublishLeavesRowRecoverableAfterRetryDelay() {
        saveEvent("event-50", "alpha-link", OffsetDateTime.parse("2026-04-03T09:00:00Z"));

        AnalyticsOutboxRecord firstClaim = jdbcAnalyticsOutboxStore.claimBatch(
                "worker-a",
                OffsetDateTime.parse("2026-04-03T09:00:10Z"),
                OffsetDateTime.parse("2026-04-03T09:01:00Z"),
                10).getFirst();
        jdbcAnalyticsOutboxStore.recordPublishFailure(
                firstClaim.id(),
                1,
                OffsetDateTime.parse("2026-04-03T09:02:00Z"),
                "RuntimeException: Kafka unavailable",
                null);
        AnalyticsOutboxRecord storedAfterFailure = findByEventId("event-50");
        List<AnalyticsOutboxRecord> blockedClaim = jdbcAnalyticsOutboxStore.claimBatch(
                "worker-b",
                OffsetDateTime.parse("2026-04-03T09:01:30Z"),
                OffsetDateTime.parse("2026-04-03T09:02:30Z"),
                10);
        List<AnalyticsOutboxRecord> recoveredClaim = jdbcAnalyticsOutboxStore.claimBatch(
                "worker-b",
                OffsetDateTime.parse("2026-04-03T09:02:01Z"),
                OffsetDateTime.parse("2026-04-03T09:03:00Z"),
                10);

        assertEquals(1, storedAfterFailure.attemptCount());
        assertEquals(OffsetDateTime.parse("2026-04-03T09:02:00Z"), storedAfterFailure.nextAttemptAt());
        assertEquals("RuntimeException: Kafka unavailable", storedAfterFailure.lastErrorSummary());
        assertNull(storedAfterFailure.claimedBy());
        assertNull(storedAfterFailure.claimedUntil());
        assertEquals(0, blockedClaim.size());
        assertEquals(1, recoveredClaim.size());
        assertEquals("event-50", recoveredClaim.getFirst().eventId());
        assertNull(recoveredClaim.getFirst().publishedAt());
        assertEquals("worker-b", recoveredClaim.getFirst().claimedBy());
    }

    @Test
    void parkedRowsAreExcludedFromClaimsAndCountedByGauge() {
        saveEvent("event-60", "alpha-link", OffsetDateTime.parse("2026-04-03T09:00:00Z"));

        AnalyticsOutboxRecord claimed = jdbcAnalyticsOutboxStore.claimBatch(
                "worker-a",
                OffsetDateTime.parse("2026-04-03T09:00:10Z"),
                OffsetDateTime.parse("2026-04-03T09:01:00Z"),
                10).getFirst();
        jdbcAnalyticsOutboxStore.recordPublishFailure(
                claimed.id(),
                5,
                null,
                "RuntimeException: Permanent failure",
                OffsetDateTime.parse("2026-04-03T09:00:20Z"));

        List<AnalyticsOutboxRecord> laterClaim = jdbcAnalyticsOutboxStore.claimBatch(
                "worker-b",
                OffsetDateTime.parse("2026-04-03T10:00:00Z"),
                OffsetDateTime.parse("2026-04-03T10:01:00Z"),
                10);

        assertTrue(laterClaim.isEmpty());
        assertEquals(0L, jdbcAnalyticsOutboxStore.countEligible(OffsetDateTime.parse("2026-04-03T10:00:00Z")));
        assertEquals(1L, jdbcAnalyticsOutboxStore.countParked());
        assertEquals(1.0, meterRegistry.get("link.analytics.outbox.parked").gauge().value());
        assertEquals(0.0, meterRegistry.get("link.analytics.outbox.eligible").gauge().value());
        assertNull(jdbcAnalyticsOutboxStore.findOldestEligibleAgeSeconds(OffsetDateTime.parse("2026-04-03T10:00:00Z")));
    }

    @Test
    void requeueMakesParkedRowEligibleAgain() {
        saveEvent("event-70", "alpha-link", OffsetDateTime.parse("2026-04-03T09:00:00Z"));

        AnalyticsOutboxRecord claimed = jdbcAnalyticsOutboxStore.claimBatch(
                "worker-a",
                OffsetDateTime.parse("2026-04-03T09:00:10Z"),
                OffsetDateTime.parse("2026-04-03T09:01:00Z"),
                10).getFirst();
        jdbcAnalyticsOutboxStore.recordPublishFailure(
                claimed.id(),
                5,
                null,
                "RuntimeException: Permanent failure",
                OffsetDateTime.parse("2026-04-03T09:00:20Z"));

        assertTrue(jdbcAnalyticsOutboxStore.requeueParked(claimed.id(), OffsetDateTime.parse("2026-04-03T09:05:00Z")));

        AnalyticsOutboxRecord stored = findByEventId("event-70");
        assertNull(stored.parkedAt());
        assertEquals(OffsetDateTime.parse("2026-04-03T09:05:00Z"), stored.nextAttemptAt());
        assertNull(stored.lastErrorSummary());
        assertTrue(jdbcAnalyticsOutboxStore.findParked(10).isEmpty());

        List<AnalyticsOutboxRecord> blockedClaim = jdbcAnalyticsOutboxStore.claimBatch(
                "worker-b",
                OffsetDateTime.parse("2026-04-03T09:04:59Z"),
                OffsetDateTime.parse("2026-04-03T09:05:30Z"),
                10);
        List<AnalyticsOutboxRecord> recoveredClaim = jdbcAnalyticsOutboxStore.claimBatch(
                "worker-b",
                OffsetDateTime.parse("2026-04-03T09:05:00Z"),
                OffsetDateTime.parse("2026-04-03T09:05:30Z"),
                10);

        assertTrue(blockedClaim.isEmpty());
        assertEquals(1, recoveredClaim.size());
        assertEquals("event-70", recoveredClaim.getFirst().eventId());
    }

    @Test
    void publishedRowsCanBeArchivedForRetention() {
        saveEvent("event-80", "alpha-link", OffsetDateTime.parse("2026-04-03T09:00:00Z"));
        saveEvent("event-81", "beta-link", OffsetDateTime.parse("2026-04-03T09:00:01Z"));

        AnalyticsOutboxRecord first = jdbcAnalyticsOutboxStore.claimBatch(
                "worker-a",
                OffsetDateTime.parse("2026-04-03T09:00:10Z"),
                OffsetDateTime.parse("2026-04-03T09:01:00Z"),
                1).getFirst();
        AnalyticsOutboxRecord second = jdbcAnalyticsOutboxStore.claimBatch(
                "worker-b",
                OffsetDateTime.parse("2026-04-03T09:00:11Z"),
                OffsetDateTime.parse("2026-04-03T09:01:00Z"),
                10).getFirst();
        jdbcAnalyticsOutboxStore.markPublished(first.id(), OffsetDateTime.parse("2026-04-03T09:05:00Z"));
        jdbcAnalyticsOutboxStore.markPublished(second.id(), OffsetDateTime.parse("2026-04-03T09:06:00Z"));

        long archived = jdbcAnalyticsOutboxStore.archivePublishedBefore(OffsetDateTime.parse("2026-04-03T09:05:30Z"), 10);

        assertEquals(1L, archived);
        assertEquals(1L, jdbcAnalyticsOutboxStore.countArchived());
        assertEquals(0L, jdbcAnalyticsOutboxStore.countUnpublished());
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM analytics_outbox WHERE event_id = 'event-81'",
                        Integer.class));
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM analytics_outbox_archive WHERE event_id = 'event-80'",
                        Integer.class));
    }

    private void saveEvent(String eventId, String slug, OffsetDateTime clickedAt) {
        jdbcAnalyticsOutboxStore.saveRedirectClickEvent(new RedirectClickAnalyticsEvent(
                eventId,
                slug,
                clickedAt,
                "test-agent",
                "https://referrer.example",
                "127.0.0.1"));
    }

    private AnalyticsOutboxRecord findByEventId(String eventId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT id, event_id, event_type, event_key, payload_json, created_at, published_at, claimed_by, claimed_until,
                       attempt_count, next_attempt_at, last_error_summary, parked_at
                FROM analytics_outbox
                WHERE event_id = ?
                """,
                (resultSet, rowNum) -> mapRecord(resultSet),
                eventId);
    }

    private AnalyticsOutboxRecord mapRecord(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new AnalyticsOutboxRecord(
                resultSet.getLong("id"),
                resultSet.getString("event_id"),
                resultSet.getString("event_type"),
                resultSet.getString("event_key"),
                resultSet.getString("payload_json"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("published_at", OffsetDateTime.class),
                resultSet.getString("claimed_by"),
                resultSet.getObject("claimed_until", OffsetDateTime.class),
                resultSet.getInt("attempt_count"),
                resultSet.getObject("next_attempt_at", OffsetDateTime.class),
                resultSet.getString("last_error_summary"),
                resultSet.getObject("parked_at", OffsetDateTime.class));
    }
}
