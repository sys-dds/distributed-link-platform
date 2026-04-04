package com.linkplatform.api.link.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class JdbcLinkLifecycleOutboxStoreTest {

    @Autowired
    private JdbcLinkLifecycleOutboxStore store;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void saveLifecycleEventPersistsStablePerLinkKey() {
        store.saveLinkLifecycleEvent(new LinkLifecycleEvent(
                "event-1",
                LinkLifecycleEventType.CREATED,
                "launch-page",
                "https://example.com/launch",
                "Launch",
                List.of("docs"),
                "example.com",
                null,
                1L,
                OffsetDateTime.parse("2026-04-04T09:00:00Z")));

        LinkLifecycleOutboxRecord stored = findByEventId("event-1");

        assertEquals("launch-page", stored.eventKey());
        assertEquals("CREATED", stored.eventType());
        org.junit.jupiter.api.Assertions.assertTrue(stored.payloadJson().contains("\"version\":1"));
        assertEquals(1.0, meterRegistry.get("link.lifecycle.outbox.unpublished").gauge().value());
        assertEquals(1.0, meterRegistry.get("link.lifecycle.outbox.eligible").gauge().value());
    }

    @Test
    void parkedRowsAreExcludedFromClaims() {
        store.saveLinkLifecycleEvent(new LinkLifecycleEvent(
                "event-2",
                LinkLifecycleEventType.UPDATED,
                "launch-page",
                "https://example.com/launch",
                "Launch",
                List.of("docs"),
                "example.com",
                null,
                2L,
                OffsetDateTime.parse("2026-04-04T09:00:00Z")));

        LinkLifecycleOutboxRecord claimed = store.claimBatch(
                "worker-a",
                OffsetDateTime.parse("2026-04-04T09:00:10Z"),
                OffsetDateTime.parse("2026-04-04T09:01:00Z"),
                10).getFirst();
        store.recordPublishFailure(
                claimed.id(),
                5,
                null,
                "RuntimeException: Permanent failure",
                OffsetDateTime.parse("2026-04-04T09:00:20Z"));

        List<LinkLifecycleOutboxRecord> laterClaim = store.claimBatch(
                "worker-b",
                OffsetDateTime.parse("2026-04-04T10:00:00Z"),
                OffsetDateTime.parse("2026-04-04T10:01:00Z"),
                10);

        assertTrue(laterClaim.isEmpty());
        assertEquals(1L, store.countParked());
        assertEquals(1.0, meterRegistry.get("link.lifecycle.outbox.parked").gauge().value());
    }

    @Test
    void requeueMakesParkedRowEligibleAgain() {
        store.saveLinkLifecycleEvent(new LinkLifecycleEvent(
                "event-3",
                LinkLifecycleEventType.DELETED,
                "launch-page",
                "https://example.com/launch",
                "Launch",
                List.of("docs"),
                "example.com",
                null,
                3L,
                OffsetDateTime.parse("2026-04-04T09:00:00Z")));

        LinkLifecycleOutboxRecord claimed = store.claimBatch(
                "worker-a",
                OffsetDateTime.parse("2026-04-04T09:00:10Z"),
                OffsetDateTime.parse("2026-04-04T09:01:00Z"),
                10).getFirst();
        store.recordPublishFailure(
                claimed.id(),
                5,
                null,
                "RuntimeException: Permanent failure",
                OffsetDateTime.parse("2026-04-04T09:00:20Z"));

        assertTrue(store.requeueParked(claimed.id(), OffsetDateTime.parse("2026-04-04T09:05:00Z")));
        LinkLifecycleOutboxRecord stored = findByEventId("event-3");
        assertNull(stored.parkedAt());
        assertEquals(OffsetDateTime.parse("2026-04-04T09:05:00Z"), stored.nextAttemptAt());
        assertNull(stored.lastErrorSummary());
    }

    private LinkLifecycleOutboxRecord findByEventId(String eventId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT id, event_id, event_type, event_key, payload_json, created_at, published_at,
                       claimed_by, claimed_until, attempt_count, next_attempt_at, last_error_summary, parked_at
                FROM link_lifecycle_outbox
                WHERE event_id = ?
                """,
                (resultSet, rowNum) -> new LinkLifecycleOutboxRecord(
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
                        resultSet.getObject("parked_at", OffsetDateTime.class)),
                eventId);
    }
}
