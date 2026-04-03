package com.linkplatform.api.link.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
                SELECT id, event_id, event_type, event_key, payload_json, created_at, published_at, claimed_by, claimed_until
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

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<List<AnalyticsOutboxRecord>> first = executor.submit(() -> claimAsync("worker-a", ready, start));
            Future<List<AnalyticsOutboxRecord>> second = executor.submit(() -> claimAsync("worker-b", ready, start));

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<AnalyticsOutboxRecord> firstClaim = first.get(5, TimeUnit.SECONDS);
            List<AnalyticsOutboxRecord> secondClaim = second.get(5, TimeUnit.SECONDS);
            Set<Long> claimedIds = ConcurrentHashMap.newKeySet();
            firstClaim.stream().map(AnalyticsOutboxRecord::id).forEach(claimedIds::add);
            secondClaim.stream().map(AnalyticsOutboxRecord::id).forEach(id -> assertTrue(claimedIds.add(id)));

            List<AnalyticsOutboxRecord> remainingClaim = jdbcAnalyticsOutboxStore.claimBatch(
                    "worker-c",
                    OffsetDateTime.parse("2026-04-03T09:00:10Z"),
                    OffsetDateTime.parse("2026-04-03T09:05:00Z"),
                    10);

            assertEquals(4, claimedIds.size() + remainingClaim.size());
        } finally {
            executor.shutdownNow();
        }
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
    void failedPublishLeavesRowRecoverableAfterLeaseExpiry() {
        saveEvent("event-50", "alpha-link", OffsetDateTime.parse("2026-04-03T09:00:00Z"));

        List<AnalyticsOutboxRecord> firstClaim = jdbcAnalyticsOutboxStore.claimBatch(
                "worker-a",
                OffsetDateTime.parse("2026-04-03T09:00:10Z"),
                OffsetDateTime.parse("2026-04-03T09:01:00Z"),
                10);
        List<AnalyticsOutboxRecord> recoveredClaim = jdbcAnalyticsOutboxStore.claimBatch(
                "worker-b",
                OffsetDateTime.parse("2026-04-03T09:01:30Z"),
                OffsetDateTime.parse("2026-04-03T09:02:00Z"),
                10);

        assertEquals(1, firstClaim.size());
        assertEquals(1, recoveredClaim.size());
        assertEquals("event-50", recoveredClaim.getFirst().eventId());
        assertNull(recoveredClaim.getFirst().publishedAt());
        assertEquals("worker-b", recoveredClaim.getFirst().claimedBy());
    }

    private List<AnalyticsOutboxRecord> claimAsync(String workerId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        return jdbcAnalyticsOutboxStore.claimBatch(
                workerId,
                OffsetDateTime.parse("2026-04-03T09:00:10Z"),
                OffsetDateTime.parse("2026-04-03T09:05:00Z"),
                2);
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
                SELECT id, event_id, event_type, event_key, payload_json, created_at, published_at, claimed_by, claimed_until
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
                resultSet.getObject("claimed_until", OffsetDateTime.class));
    }
}
