package com.linkplatform.api.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "link-platform.projection-jobs.chunk-size=2")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class RecoveryConvergenceIntegrationTest {

    private static final String FREE_API_KEY = "free-owner-api-key";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProjectionJobService projectionJobService;

    @Autowired
    private ProjectionJobRunner projectionJobRunner;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void restoreReplayAndRebuildConvergeOnOwnerFacingReads() throws Exception {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-04-01T08:00:00Z");
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-04-02T08:00:00Z");
        insertLink(1L, "restore-link", "https://docs.example.com/restore-v2", createdAt, 2L);
        insertClick("restore-link", OffsetDateTime.parse("2026-04-06T07:00:00Z"));
        insertClick("restore-link", OffsetDateTime.parse("2026-04-06T08:00:00Z"));
        insertLifecycleHistory(1L, 1L, "event-1", "CREATED", "restore-link", "https://docs.example.com/restore", "Restore", "[\"docs\"]", "docs.example.com", null, 1L, createdAt);
        insertLifecycleHistory(1L, 2L, "event-2", "UPDATED", "restore-link", "https://docs.example.com/restore-v2", "Restore v2", "[\"docs\",\"beta\"]", "docs.example.com", null, 2L, updatedAt);

        RecoverySnapshot snapshot = captureSnapshot();

        wipeRecoveredState();
        restoreSnapshot(snapshot);

        projectionJobService.createJob(ProjectionJobType.ACTIVITY_FEED_REPLAY);
        projectionJobService.createJob(ProjectionJobType.CLICK_ROLLUP_REBUILD);
        projectionJobService.createJob(ProjectionJobType.LINK_CATALOG_REBUILD);
        projectionJobService.createJob(ProjectionJobType.LINK_DISCOVERY_REBUILD);
        for (int index = 0; index < 6; index++) {
            projectionJobRunner.runPendingJobs();
        }

        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM link_click_daily_rollups", Integer.class));
        mockMvc.perform(get("/api/v1/links/restore-link").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalUrl").value("https://docs.example.com/restore-v2"))
                .andExpect(jsonPath("$.version").value(2));
        mockMvc.perform(get("/api/v1/links/discovery")
                        .param("search", "restore")
                        .header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].slug").value("restore-link"))
                .andExpect(jsonPath("$.items[0].originalUrl").value("https://docs.example.com/restore-v2"));
        mockMvc.perform(get("/api/v1/links/activity").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("restore-link"))
                .andExpect(jsonPath("$[0].type").value("updated"));
        mockMvc.perform(get("/api/v1/links/restore-link/traffic-summary").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(2))
                .andExpect(jsonPath("$.clicksLast24Hours").value(2));
    }

    private RecoverySnapshot captureSnapshot() {
        return new RecoverySnapshot(
                jdbcTemplate.query(
                        """
                        SELECT slug, original_url, created_at, expires_at, title, tags_json, hostname, version, owner_id
                        FROM links
                        ORDER BY slug
                        """,
                        (resultSet, rowNum) -> new LinkRow(
                                resultSet.getString("slug"),
                                resultSet.getString("original_url"),
                                resultSet.getObject("created_at", OffsetDateTime.class),
                                resultSet.getObject("expires_at", OffsetDateTime.class),
                                resultSet.getString("title"),
                                resultSet.getString("tags_json"),
                                resultSet.getString("hostname"),
                                resultSet.getLong("version"),
                                resultSet.getLong("owner_id"))),
                jdbcTemplate.query(
                        """
                        SELECT id, event_id, slug, clicked_at, user_agent, referrer, remote_address
                        FROM link_clicks
                        ORDER BY id
                        """,
                        (resultSet, rowNum) -> new ClickRow(
                                resultSet.getLong("id"),
                                resultSet.getString("event_id"),
                                resultSet.getString("slug"),
                                resultSet.getObject("clicked_at", OffsetDateTime.class),
                                resultSet.getString("user_agent"),
                                resultSet.getString("referrer"),
                                resultSet.getString("remote_address"))),
                jdbcTemplate.query(
                        """
                        SELECT id, event_id, event_type, event_key, payload_json, created_at, published_at,
                               claimed_by, claimed_until, attempt_count, next_attempt_at, last_error_summary, parked_at
                        FROM link_lifecycle_outbox
                        ORDER BY id
                        """,
                        (resultSet, rowNum) -> new LifecycleOutboxRow(
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
                                resultSet.getObject("parked_at", OffsetDateTime.class))));
    }

    private void wipeRecoveredState() {
        jdbcTemplate.update("DELETE FROM link_click_daily_rollups");
        jdbcTemplate.update("DELETE FROM link_activity_events");
        jdbcTemplate.update("DELETE FROM link_catalog_projection");
        jdbcTemplate.update("DELETE FROM link_discovery_projection");
        jdbcTemplate.update("DELETE FROM projection_jobs");
        jdbcTemplate.update("DELETE FROM link_clicks");
        jdbcTemplate.update("DELETE FROM link_lifecycle_outbox");
        jdbcTemplate.update("DELETE FROM links");
    }

    private void restoreSnapshot(RecoverySnapshot snapshot) {
        snapshot.links().forEach(link -> jdbcTemplate.update(
                """
                INSERT INTO links (slug, original_url, created_at, expires_at, title, tags_json, hostname, version, owner_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                link.slug(),
                link.originalUrl(),
                link.createdAt(),
                link.expiresAt(),
                link.title(),
                link.tagsJson(),
                link.hostname(),
                link.version(),
                link.ownerId()));
        snapshot.clicks().forEach(click -> jdbcTemplate.update(
                """
                INSERT INTO link_clicks (id, event_id, slug, clicked_at, user_agent, referrer, remote_address)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                click.id(),
                click.eventId(),
                click.slug(),
                click.clickedAt(),
                click.userAgent(),
                click.referrer(),
                click.remoteAddress()));
        snapshot.lifecycleOutboxRows().forEach(row -> jdbcTemplate.update(
                """
                INSERT INTO link_lifecycle_outbox (
                    id, event_id, event_type, event_key, payload_json, created_at, published_at,
                    claimed_by, claimed_until, attempt_count, next_attempt_at, last_error_summary, parked_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                row.id(),
                row.eventId(),
                row.eventType(),
                row.eventKey(),
                row.payloadJson(),
                row.createdAt(),
                row.publishedAt(),
                row.claimedBy(),
                row.claimedUntil(),
                row.attemptCount(),
                row.nextAttemptAt(),
                row.lastErrorSummary(),
                row.parkedAt()));
    }

    private void insertLink(long ownerId, String slug, String originalUrl, OffsetDateTime createdAt, long version) {
        jdbcTemplate.update(
                """
                INSERT INTO links (slug, original_url, created_at, hostname, version, owner_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                slug,
                originalUrl,
                createdAt,
                URI.create(originalUrl).getHost().toLowerCase(),
                version,
                ownerId);
    }

    private void insertClick(String slug, OffsetDateTime clickedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO link_clicks (event_id, slug, clicked_at, user_agent, referrer, remote_address)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                slug + "-" + clickedAt.toInstant().toEpochMilli(),
                slug,
                clickedAt,
                "test-agent",
                "https://referrer.example",
                "127.0.0.1");
    }

    private void insertLifecycleHistory(
            long ownerId,
            long id,
            String eventId,
            String eventType,
            String slug,
            String originalUrl,
            String title,
            String tagsJson,
            String hostname,
            OffsetDateTime expiresAt,
            long version,
            OffsetDateTime occurredAt) {
        jdbcTemplate.update(
                """
                INSERT INTO link_lifecycle_outbox (
                    id, event_id, event_type, event_key, payload_json, created_at, published_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                eventId,
                eventType,
                slug,
                """
                {"eventId":"%s","eventType":"%s","ownerId":%d,"slug":"%s","originalUrl":"%s","title":%s,"tags":%s,"hostname":"%s","expiresAt":%s,"version":%d,"occurredAt":"%s"}
                """.formatted(
                        eventId,
                        eventType,
                        ownerId,
                        slug,
                        originalUrl,
                        title == null ? "null" : "\"" + title + "\"",
                        tagsJson == null ? "null" : tagsJson,
                        hostname,
                        expiresAt == null ? "null" : "\"" + expiresAt + "\"",
                        version,
                        occurredAt),
                occurredAt,
                occurredAt);
    }

    private record RecoverySnapshot(
            List<LinkRow> links,
            List<ClickRow> clicks,
            List<LifecycleOutboxRow> lifecycleOutboxRows) {
    }

    private record LinkRow(
            String slug,
            String originalUrl,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt,
            String title,
            String tagsJson,
            String hostname,
            long version,
            long ownerId) {
    }

    private record ClickRow(
            long id,
            String eventId,
            String slug,
            OffsetDateTime clickedAt,
            String userAgent,
            String referrer,
            String remoteAddress) {
    }

    private record LifecycleOutboxRow(
            long id,
            String eventId,
            String eventType,
            String eventKey,
            String payloadJson,
            OffsetDateTime createdAt,
            OffsetDateTime publishedAt,
            String claimedBy,
            OffsetDateTime claimedUntil,
            int attemptCount,
            OffsetDateTime nextAttemptAt,
            String lastErrorSummary,
            OffsetDateTime parkedAt) {
    }
}
