package com.linkplatform.api.link.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
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
                SELECT id, event_id, event_type, event_key, payload_json, created_at, published_at
                FROM analytics_outbox
                WHERE event_id = ?
                """,
                (resultSet, rowNum) -> new AnalyticsOutboxRecord(
                        resultSet.getLong("id"),
                        resultSet.getString("event_id"),
                        resultSet.getString("event_type"),
                        resultSet.getString("event_key"),
                        resultSet.getString("payload_json"),
                        resultSet.getObject("created_at", OffsetDateTime.class),
                        resultSet.getObject("published_at", OffsetDateTime.class)),
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
}
