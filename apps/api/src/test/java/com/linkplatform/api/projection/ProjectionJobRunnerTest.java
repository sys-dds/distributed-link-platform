package com.linkplatform.api.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ProjectionJobRunnerTest {

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

    @Test
    void activityFeedReplayRestoresFeedFromLifecycleHistoryIdempotently() throws Exception {
        insertLifecycleHistory("event-1", "CREATED", "alpha", "https://example.com/alpha", "Alpha", "[\"docs\"]", "example.com", null, OffsetDateTime.parse("2026-04-04T09:00:00Z"));
        insertLifecycleHistory("event-2", "DELETED", "gone", "https://example.com/gone", "Gone", "[\"archived\"]", "example.com", null, OffsetDateTime.parse("2026-04-04T09:10:00Z"));

        ProjectionJob firstJob = projectionJobService.createJob(ProjectionJobType.ACTIVITY_FEED_REPLAY);
        projectionJobRunner.runPendingJobs();

        assertCount("SELECT COUNT(*) FROM link_activity_events", 2);
        assertCompleted(firstJob.id(), 2L);

        ProjectionJob secondJob = projectionJobService.createJob(ProjectionJobType.ACTIVITY_FEED_REPLAY);
        projectionJobRunner.runPendingJobs();

        assertCount("SELECT COUNT(*) FROM link_activity_events", 2);
        assertCompleted(secondJob.id(), 2L);
        mockMvc.perform(get("/api/v1/links/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("deleted"))
                .andExpect(jsonPath("$[0].slug").value("gone"))
                .andExpect(jsonPath("$[0].originalUrl").value("https://example.com/gone"));
        assertEquals(2.0, meterRegistry.get("link.projection.jobs.completed").counter().count());
    }

    @Test
    void clickRollupRebuildRestoresDeterministicReportingFromClickHistory() throws Exception {
        insertLink("alpha", "https://example.com/alpha", OffsetDateTime.parse("2026-04-01T08:00:00Z"));
        insertLink("beta", "https://example.com/beta", OffsetDateTime.parse("2026-04-01T08:00:00Z"));
        insertClick("alpha", OffsetDateTime.now().minusHours(1));
        insertClick("alpha", OffsetDateTime.now().minusHours(2));
        insertClick("beta", OffsetDateTime.now().minusHours(3));
        jdbcTemplate.update("DELETE FROM link_click_daily_rollups");

        ProjectionJob job = projectionJobService.createJob(ProjectionJobType.CLICK_ROLLUP_REBUILD);
        projectionJobRunner.runPendingJobs();

        assertCompleted(job.id(), 2L);
        mockMvc.perform(get("/api/v1/links/alpha/traffic-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(2))
                .andExpect(jsonPath("$.clicksLast7Days").value(2));
        mockMvc.perform(get("/api/v1/links/traffic/top").param("window", "24h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("alpha"))
                .andExpect(jsonPath("$[0].clickTotal").value(2));
        mockMvc.perform(get("/api/v1/links/traffic/trending").param("window", "24h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("alpha"));
    }

    private void assertCompleted(long jobId, long processedCount) {
        ProjectionJob job = jdbcTemplate.queryForObject(
                """
                SELECT id, job_type, status, requested_at, started_at, completed_at,
                       processed_count, error_summary, claimed_by, claimed_until
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
                        resultSet.getString("error_summary"),
                        resultSet.getString("claimed_by"),
                        resultSet.getObject("claimed_until", OffsetDateTime.class)),
                jobId);
        assertEquals(ProjectionJobStatus.COMPLETED, job.status());
        assertEquals(processedCount, job.processedCount());
    }

    private void insertLifecycleHistory(
            String eventId,
            String eventType,
            String slug,
            String originalUrl,
            String title,
            String tagsJson,
            String hostname,
            OffsetDateTime expiresAt,
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
                {"eventId":"%s","eventType":"%s","slug":"%s","originalUrl":"%s","title":%s,"tags":%s,"hostname":"%s","expiresAt":%s,"occurredAt":"%s"}
                """.formatted(
                        eventId,
                        eventType,
                        slug,
                        originalUrl,
                        title == null ? "null" : "\"" + title + "\"",
                        tagsJson == null ? "null" : tagsJson,
                        hostname,
                        expiresAt == null ? "null" : "\"" + expiresAt + "\"",
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
