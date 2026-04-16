package com.linkplatform.api.link.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
class AnalyticsOutboxLeaseReclaimIntegrationTest {

    @Autowired
    private AnalyticsOutboxStore analyticsOutboxStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void expiredClaimedRecordIsReclaimedWithDurableLeaseDebuggingTruth() {
        OffsetDateTime now = OffsetDateTime.parse("2026-04-16T08:00:00Z");
        long id = insertClaimedOutboxRecord("stalled-worker", now.minusMinutes(5));

        List<AnalyticsOutboxRecord> claimed = analyticsOutboxStore.claimBatch(
                "reclaim-worker",
                now,
                now.plusMinutes(1),
                10);

        assertEquals(1, claimed.size());
        AnalyticsOutboxRecord record = claimed.getFirst();
        assertEquals(id, record.id());
        assertEquals(1, claimed.stream().map(AnalyticsOutboxRecord::id).distinct().count());
        assertEquals("reclaim-worker", record.claimedBy());
        assertEquals(now.plusMinutes(1), record.claimedUntil());
        assertNotNull(record.lastErrorSummary());
        assertTrue(record.lastErrorSummary().contains("lease-expired"));
        assertTrue(record.lastErrorSummary().contains("previous_claimed_by=stalled-worker"));
        assertTrue(record.lastErrorSummary().contains("reclaimed_by=reclaim-worker"));

        analyticsOutboxStore.markPublished(id, now.plusMinutes(2));
        analyticsOutboxStore.markPublished(id, now.plusMinutes(3));

        assertEquals(1L, count("SELECT COUNT(*) FROM analytics_outbox WHERE id = ? AND published_at IS NOT NULL", id));
        assertEquals(0L, analyticsOutboxStore.countEligible(now.plusMinutes(4)));
    }

    private long insertClaimedOutboxRecord(String claimedBy, OffsetDateTime claimedUntil) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO analytics_outbox (
                    event_id, event_type, event_key, payload_json, claimed_by, claimed_until
                ) VALUES ('lease-event-1', 'redirect-click', 'lease-key', '{}', ?, ?)
                RETURNING id
                """,
                Long.class,
                claimedBy,
                claimedUntil);
    }

    private long count(String sql, Object... parameters) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, parameters);
        return count == null ? 0L : count;
    }
}
