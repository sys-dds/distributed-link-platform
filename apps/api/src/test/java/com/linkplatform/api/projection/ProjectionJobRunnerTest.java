package com.linkplatform.api.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "link-platform.projection-jobs.chunk-size=2")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ProjectionJobRunnerTest {

    private static final String FREE_API_KEY = "free-owner-api-key";

    @Autowired
    private ProjectionJobService projectionJobService;

    @Autowired
    private ProjectionJobRunner projectionJobRunner;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @SpyBean
    private com.linkplatform.api.link.application.LinkReadCache linkReadCache;

    @Test
    void activityFeedReplayProcessesInChunksAndCompletesIdempotently() throws Exception {
        insertLifecycleHistory(1L, "event-1", "CREATED", "alpha", "https://example.com/alpha", "Alpha", "[\"docs\"]", "example.com", null, 1L, OffsetDateTime.parse("2026-04-04T09:00:00Z"));
        insertLifecycleHistory(1L, "event-2", "UPDATED", "alpha", "https://example.com/alpha-v2", "Alpha v2", "[\"docs\"]", "example.com", null, 2L, OffsetDateTime.parse("2026-04-04T09:05:00Z"));
        insertLifecycleHistory(1L, "event-3", "DELETED", "gone", "https://example.com/gone", "Gone", "[\"archived\"]", "example.com", null, 1L, OffsetDateTime.parse("2026-04-04T09:10:00Z"));

        ProjectionJob firstJob = projectionJobService.createJob(ProjectionJobType.ACTIVITY_FEED_REPLAY);

        projectionJobRunner.runPendingJobs();
        assertJob(firstJob.id(), ProjectionJobStatus.QUEUED, 2L, 2L);
        assertCount("SELECT COUNT(*) FROM link_activity_events", 2);

        projectionJobRunner.runPendingJobs();
        assertJob(firstJob.id(), ProjectionJobStatus.COMPLETED, 3L, 3L);
        assertCount("SELECT COUNT(*) FROM link_activity_events", 3);

        ProjectionJob secondJob = projectionJobService.createJob(ProjectionJobType.ACTIVITY_FEED_REPLAY);
        projectionJobRunner.runPendingJobs();
        projectionJobRunner.runPendingJobs();

        assertCount("SELECT COUNT(*) FROM link_activity_events", 3);
        assertJob(secondJob.id(), ProjectionJobStatus.COMPLETED, 3L, 3L);
        mockMvc.perform(get("/api/v1/links/activity").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("deleted"))
                .andExpect(jsonPath("$[0].slug").value("gone"))
                .andExpect(jsonPath("$[0].originalUrl").value("https://example.com/gone"));
        verify(linkReadCache, atLeastOnce()).invalidateOwnerAnalytics(1L);
        assertEquals(2.0, meterRegistry.get("link.projection.jobs.completed").counter().count());
    }

    @Test
    void emptyProjectionJobsCompleteWithoutCheckpointFailures() {
        for (ProjectionJobType jobType : ProjectionJobType.values()) {
            ProjectionJob job = projectionJobService.createJob(jobType);
            projectionJobRunner.runPendingJobs();
            assertJob(job.id(), ProjectionJobStatus.COMPLETED, 0L, null);
        }
    }

    @Test
    void clickRollupRebuildResumesFromCheckpointAfterFailureStatus() throws Exception {
        insertLink("alpha", "https://example.com/alpha", OffsetDateTime.parse("2026-04-01T08:00:00Z"));
        insertLink("beta", "https://example.com/beta", OffsetDateTime.parse("2026-04-01T08:00:00Z"));
        insertCatalogProjection("alpha", "https://example.com/alpha", OffsetDateTime.parse("2026-04-01T08:00:00Z"), null, null, null, "example.com", null);
        insertCatalogProjection("beta", "https://example.com/beta", OffsetDateTime.parse("2026-04-01T08:00:00Z"), null, null, null, "example.com", null);
        insertClick("alpha", OffsetDateTime.now().minusHours(1));
        insertClick("alpha", OffsetDateTime.now().minusHours(2));
        insertClick("beta", OffsetDateTime.now().minusHours(3));
        jdbcTemplate.update("DELETE FROM link_click_daily_rollups");

        ProjectionJob job = projectionJobService.createJob(ProjectionJobType.CLICK_ROLLUP_REBUILD);
        projectionJobRunner.runPendingJobs();

        assertJob(job.id(), ProjectionJobStatus.QUEUED, 2L, 2L);
        jdbcTemplate.update(
                "UPDATE projection_jobs SET status = 'FAILED', error_summary = 'simulated' WHERE id = ?",
                job.id());

        projectionJobRunner.runPendingJobs();

        assertJob(job.id(), ProjectionJobStatus.COMPLETED, 3L, 3L);
        mockMvc.perform(get("/api/v1/links/alpha/traffic-summary").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(2))
                .andExpect(jsonPath("$.clicksLast7Days").value(2));
        mockMvc.perform(get("/api/v1/links/traffic/top").param("window", "24h").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("alpha"))
                .andExpect(jsonPath("$[0].clickTotal").value(2));
        verify(linkReadCache, atLeastOnce()).invalidateOwnerAnalytics(1L);
    }

    @Test
    void linkCatalogRebuildReplaysLifecycleHistoryIntoControlPlaneProjection() throws Exception {
        insertLifecycleHistory(1L, "event-1", "CREATED", "launch-page", "https://docs.example.com/launch", "Launch", "[\"docs\"]", "docs.example.com", null, 1L, OffsetDateTime.parse("2026-04-04T09:00:00Z"));
        insertLifecycleHistory(1L, "event-2", "UPDATED", "launch-page", "https://docs.example.com/launch-v2", "Launch v2", "[\"docs\",\"product\"]", "docs.example.com", OffsetDateTime.parse("2030-04-01T08:00:00Z"), 2L, OffsetDateTime.parse("2026-04-04T09:05:00Z"));
        insertLifecycleHistory(2L, "event-3", "CREATED", "other-owner", "https://example.com/other", "Other", "[\"other\"]", "example.com", null, 1L, OffsetDateTime.parse("2026-04-04T09:06:00Z"));
        insertLifecycleHistory(1L, "event-4", "CREATED", "delete-me", "https://example.com/delete", "Delete", "[\"archived\"]", "example.com", null, 1L, OffsetDateTime.parse("2026-04-04T09:07:00Z"));
        insertLifecycleHistory(1L, "event-5", "DELETED", "delete-me", "https://example.com/delete", "Delete", "[\"archived\"]", "example.com", null, 2L, OffsetDateTime.parse("2026-04-04T09:08:00Z"));

        ProjectionJob job = projectionJobService.createJob(ProjectionJobType.LINK_CATALOG_REBUILD);
        projectionJobRunner.runPendingJobs();
        projectionJobRunner.runPendingJobs();
        projectionJobRunner.runPendingJobs();

        assertJob(job.id(), ProjectionJobStatus.COMPLETED, 5L, 5L);
        mockMvc.perform(get("/api/v1/links").param("state", "all").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("launch-page"))
                .andExpect(jsonPath("$[0].originalUrl").value("https://docs.example.com/launch-v2"))
                .andExpect(jsonPath("$[0].title").value("Launch v2"))
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[0].tags[1]").value("product"));
        verify(linkReadCache, atLeastOnce()).invalidateOwnerControlPlane(1L);
    }

    @Test
    void linkCatalogRebuildPreservesOwnerScopedProjectionResults() throws Exception {
        insertLifecycleHistory(1L, "event-a", "CREATED", "free-only", "https://example.com/free", "Free", "[\"docs\"]", "example.com", null, 1L, OffsetDateTime.parse("2026-04-04T10:00:00Z"));
        insertLifecycleHistory(2L, "event-b", "CREATED", "pro-only", "https://example.com/pro", "Pro", "[\"product\"]", "example.com", null, 1L, OffsetDateTime.parse("2026-04-04T10:01:00Z"));

        ProjectionJob job = projectionJobService.createJob(ProjectionJobType.LINK_CATALOG_REBUILD);
        projectionJobRunner.runPendingJobs();

        mockMvc.perform(get("/api/v1/links").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("free-only"));
    }

    @Test
    void linkDiscoveryRebuildReconstructsOwnerScopedDiscoveryProjection() throws Exception {
        insertLifecycleHistory(1L, "event-d1", "CREATED", "docs-link", "https://docs.example.com/link", "Docs", "[\"docs\"]", "docs.example.com", null, 1L, OffsetDateTime.parse("2026-04-04T11:00:00Z"));
        insertLifecycleHistory(1L, "event-d2", "UPDATED", "docs-link", "https://docs.example.com/link-v2", "Docs v2", "[\"docs\",\"beta\"]", "docs.example.com", null, 2L, OffsetDateTime.parse("2026-04-04T12:00:00Z"));
        insertLifecycleHistory(2L, "event-d3", "CREATED", "other-owner", "https://app.example.com/other", "Other", "[\"product\"]", "app.example.com", null, 1L, OffsetDateTime.parse("2026-04-04T12:05:00Z"));

        ProjectionJob job = projectionJobService.createJob(ProjectionJobType.LINK_DISCOVERY_REBUILD);
        projectionJobRunner.runPendingJobs();
        projectionJobRunner.runPendingJobs();

        assertJob(job.id(), ProjectionJobStatus.COMPLETED, 3L, 3L);
        mockMvc.perform(get("/api/v1/links/discovery")
                        .param("hostname", "docs.example.com")
                        .param("tag", "docs")
                        .header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("docs-link"))
                .andExpect(jsonPath("$.items[0].originalUrl").value("https://docs.example.com/link-v2"))
                .andExpect(jsonPath("$.items[0].version").value(2))
                .andExpect(jsonPath("$.items[0].tags[1]").value("beta"));
        verify(linkReadCache, atLeastOnce()).invalidateOwnerControlPlane(1L);
    }

    private void assertJob(long jobId, ProjectionJobStatus status, long processedCount, Long checkpointId) {
        ProjectionJob job = jdbcTemplate.queryForObject(
                """
                SELECT id, job_type, status, requested_at, started_at, completed_at,
                       processed_count, checkpoint_id, error_summary, claimed_by, claimed_until, owner_id, workspace_id, slug,
                       range_start, range_end, requested_by_owner_id, operator_note
                FROM projection_jobs
                WHERE id = ?
                """,
                (resultSet, rowNum) -> new ProjectionJob(
                        resultSet.getLong("id"),
                        ProjectionJobType.valueOf(resultSet.getString("job_type")),
                        ProjectionJobStatus.valueOf(resultSet.getString("status")),
                        resultSet.getObject("requested_at", OffsetDateTime.class),
                        resultSet.getObject("started_at", OffsetDateTime.class),
                        resultSet.getObject("completed_at", OffsetDateTime.class),
                        resultSet.getLong("processed_count"),
                        resultSet.getObject("checkpoint_id", Long.class),
                        resultSet.getString("error_summary"),
                        resultSet.getString("claimed_by"),
                        resultSet.getObject("claimed_until", OffsetDateTime.class),
                        resultSet.getObject("owner_id", Long.class),
                        resultSet.getObject("workspace_id", Long.class),
                        resultSet.getString("slug"),
                        resultSet.getObject("range_start", OffsetDateTime.class),
                        resultSet.getObject("range_end", OffsetDateTime.class),
                        resultSet.getObject("requested_by_owner_id", Long.class),
                        resultSet.getString("operator_note")),
                jobId);
        assertEquals(status, job.status());
        assertEquals(processedCount, job.processedCount());
        assertEquals(checkpointId, job.checkpointId());
    }

    private void insertLifecycleHistory(
            long ownerId,
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
                INSERT INTO link_lifecycle_outbox (event_id, event_type, event_key, payload_json, created_at, published_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
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

    private void insertLink(String slug, String originalUrl, OffsetDateTime createdAt) {
        jdbcTemplate.update(
                "INSERT INTO links (slug, original_url, created_at, hostname) VALUES (?, ?, ?, ?)",
                slug,
                originalUrl,
                createdAt,
                java.net.URI.create(originalUrl).getHost().toLowerCase());
    }

    private void insertCatalogProjection(
            String slug,
            String originalUrl,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            String title,
            String tagsJson,
            String hostname,
            OffsetDateTime expiresAt) {
        jdbcTemplate.update(
                """
                INSERT INTO link_catalog_projection (
                    slug, original_url, created_at, updated_at, title, tags_json, hostname, expires_at, deleted_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, 1)
                """,
                slug,
                originalUrl,
                createdAt,
                updatedAt == null ? createdAt : updatedAt,
                title,
                tagsJson,
                hostname,
                expiresAt);
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

    private void assertCount(String sql, int expectedCount) {
        Integer actualCount = jdbcTemplate.queryForObject(sql, Integer.class);
        assertEquals(expectedCount, actualCount);
    }
}
