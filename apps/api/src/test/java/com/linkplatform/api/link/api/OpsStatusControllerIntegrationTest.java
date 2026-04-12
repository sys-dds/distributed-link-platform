package com.linkplatform.api.link.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
class OpsStatusControllerIntegrationTest {

    private static final String FREE_API_KEY = "free-owner-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void opsReadCanFetchStatusAndResponseUsesRealWorkspaceScopedState() throws Exception {
        createWorkspace("ops-status");
        createWorkspace("ops-other");
        String opsKey = bootstrapWorkspaceApiKey("ops-status", "ops-status-read", "[\"ops:read\",\"ops:write\"]");
        String deniedKey = bootstrapWorkspaceApiKey("ops-status", "ops-status-links", "[\"links:read\"]");

        jdbcTemplate.update(
                "UPDATE pipeline_controls SET paused = TRUE, last_force_tick_at = ?, last_requeue_at = ?, last_relay_success_at = ?, last_relay_failure_at = ?, last_relay_failure_reason = ? WHERE pipeline_name = 'analytics'",
                OffsetDateTime.parse("2026-04-07T08:00:00Z"),
                OffsetDateTime.parse("2026-04-07T08:01:00Z"),
                OffsetDateTime.parse("2026-04-07T08:02:00Z"),
                OffsetDateTime.parse("2026-04-07T08:03:00Z"),
                "analytics relay failed");
        jdbcTemplate.update(
                "UPDATE pipeline_controls SET paused = FALSE, last_force_tick_at = ?, last_requeue_at = ?, last_relay_success_at = ? WHERE pipeline_name = 'lifecycle'",
                OffsetDateTime.parse("2026-04-07T09:00:00Z"),
                OffsetDateTime.parse("2026-04-07T09:01:00Z"),
                OffsetDateTime.parse("2026-04-07T09:02:00Z"));

        insertAnalyticsOutbox("analytics-eligible", OffsetDateTime.parse("2026-04-07T07:00:00Z"), null);
        insertAnalyticsOutbox("analytics-parked", OffsetDateTime.parse("2026-04-07T06:00:00Z"), OffsetDateTime.parse("2026-04-07T06:30:00Z"));
        insertLifecycleOutbox("lifecycle-eligible", OffsetDateTime.parse("2026-04-07T07:15:00Z"), null);
        insertLifecycleOutbox("lifecycle-parked", OffsetDateTime.parse("2026-04-07T06:15:00Z"), OffsetDateTime.parse("2026-04-07T06:45:00Z"));

        long workspaceId = jdbcTemplate.queryForObject("SELECT id FROM workspaces WHERE slug = 'ops-status'", Long.class);
        long otherWorkspaceId = jdbcTemplate.queryForObject("SELECT id FROM workspaces WHERE slug = 'ops-other'", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO projection_jobs (job_type, status, requested_at, started_at, completed_at, workspace_id, requested_by_owner_id)
                VALUES ('CLICK_ROLLUP_REBUILD', 'QUEUED', ?, NULL, NULL, ?, 1),
                       ('CLICK_ROLLUP_REBUILD', 'RUNNING', ?, ?, NULL, ?, 1),
                       ('CLICK_ROLLUP_REBUILD', 'FAILED', ?, ?, ?, ?, 1),
                       ('CLICK_ROLLUP_REBUILD', 'COMPLETED', ?, ?, ?, ?, 1),
                       ('CLICK_ROLLUP_REBUILD', 'FAILED', ?, ?, ?, ?, 1)
                """,
                OffsetDateTime.parse("2026-04-07T07:00:00Z"), workspaceId,
                OffsetDateTime.parse("2026-04-07T07:05:00Z"), OffsetDateTime.parse("2026-04-07T07:06:00Z"), workspaceId,
                OffsetDateTime.parse("2026-04-07T07:10:00Z"), OffsetDateTime.parse("2026-04-07T07:11:00Z"), OffsetDateTime.parse("2026-04-07T07:12:00Z"), workspaceId,
                OffsetDateTime.parse("2026-04-07T07:20:00Z"), OffsetDateTime.parse("2026-04-07T07:21:00Z"), OffsetDateTime.parse("2026-04-07T07:22:00Z"), workspaceId,
                OffsetDateTime.parse("2026-04-07T07:25:00Z"), OffsetDateTime.parse("2026-04-07T07:26:00Z"), OffsetDateTime.parse("2026-04-07T07:27:00Z"), otherWorkspaceId);

        jdbcTemplate.update(
                """
                INSERT INTO link_abuse_cases (workspace_id, slug, status, source, signal_count, risk_score, summary, created_at, updated_at, reviewed_at)
                VALUES (?, 'a-open', 'OPEN', 'MANUAL_OPERATOR', 1, 10, 'open', ?, ?, NULL),
                       (?, 'a-quarantined', 'QUARANTINED', 'REDIRECT_RATE_LIMIT', 2, 80, 'quarantined', ?, ?, ?),
                       (?, 'a-released', 'RELEASED', 'MANUAL_OPERATOR', 1, 20, 'released', ?, ?, CURRENT_TIMESTAMP),
                       (?, 'a-dismissed', 'DISMISSED', 'MANUAL_OPERATOR', 1, 20, 'dismissed', ?, ?, CURRENT_TIMESTAMP),
                       (?, 'other-open', 'OPEN', 'MANUAL_OPERATOR', 1, 10, 'other', ?, ?, NULL)
                """,
                workspaceId, OffsetDateTime.parse("2026-04-07T07:00:00Z"), OffsetDateTime.parse("2026-04-07T07:00:00Z"),
                workspaceId, OffsetDateTime.parse("2026-04-07T07:10:00Z"), OffsetDateTime.parse("2026-04-07T07:11:00Z"), OffsetDateTime.parse("2026-04-07T07:11:00Z"),
                workspaceId, OffsetDateTime.parse("2026-04-07T08:00:00Z"), OffsetDateTime.parse("2026-04-07T08:05:00Z"),
                workspaceId, OffsetDateTime.parse("2026-04-07T08:10:00Z"), OffsetDateTime.parse("2026-04-07T08:15:00Z"),
                otherWorkspaceId, OffsetDateTime.parse("2026-04-07T09:00:00Z"), OffsetDateTime.parse("2026-04-07T09:00:00Z"));

        mockMvc.perform(get("/api/v1/ops/status")
                        .header("X-API-Key", deniedKey)
                        .header("X-Workspace-Slug", "ops-status"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/ops/status")
                        .header("X-API-Key", opsKey)
                        .header("X-Workspace-Slug", "ops-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceSlug").value("ops-status"))
                .andExpect(jsonPath("$.runtimeMode").isNotEmpty())
                .andExpect(jsonPath("$.pipelines.analytics.paused").value(true))
                .andExpect(jsonPath("$.pipelines.analytics.eligibleCount").value(1))
                .andExpect(jsonPath("$.pipelines.analytics.parkedCount").value(1))
                .andExpect(jsonPath("$.pipelines.analytics.lastRelayFailureReason").value("analytics relay failed"))
                .andExpect(jsonPath("$.pipelines.lifecycle.eligibleCount").value(1))
                .andExpect(jsonPath("$.projectionJobs.queuedCount").value(1))
                .andExpect(jsonPath("$.projectionJobs.runningCount").value(1))
                .andExpect(jsonPath("$.projectionJobs.failedCount").value(1))
                .andExpect(jsonPath("$.projectionJobs.completedCount").value(1))
                .andExpect(jsonPath("$.abuse.openCount").value(1))
                .andExpect(jsonPath("$.abuse.quarantinedCount").value(1))
                .andExpect(jsonPath("$.abuse.releasedTodayCount").value(1))
                .andExpect(jsonPath("$.abuse.dismissedTodayCount").value(1))
                .andExpect(jsonPath("$.governance.totalWorkspaces").isNumber());
    }

    private void createWorkspace(String slug) {
        jdbcTemplate.update(
                "INSERT INTO workspaces (slug, display_name, personal_workspace, created_at, created_by_owner_id) VALUES (?, ?, FALSE, ?, 1)",
                slug,
                slug,
                OffsetDateTime.now());
        Long workspaceId = jdbcTemplate.queryForObject("SELECT id FROM workspaces WHERE slug = ?", Long.class, slug);
        jdbcTemplate.update(
                "INSERT INTO workspace_members (workspace_id, owner_id, role, joined_at, added_by_owner_id) VALUES (?, 1, 'OWNER', ?, 1)",
                workspaceId,
                OffsetDateTime.now());
    }

    private void insertAnalyticsOutbox(String eventId, OffsetDateTime createdAt, OffsetDateTime parkedAt) {
        jdbcTemplate.update(
                "INSERT INTO analytics_outbox (event_id, event_type, event_key, payload_json, created_at, parked_at, attempt_count) VALUES (?, 'redirect-click', ?, '{}', ?, ?, 2)",
                eventId,
                eventId,
                createdAt,
                parkedAt);
    }

    private void insertLifecycleOutbox(String eventId, OffsetDateTime createdAt, OffsetDateTime parkedAt) {
        jdbcTemplate.update(
                "INSERT INTO link_lifecycle_outbox (event_id, event_type, event_key, payload_json, created_at, parked_at, attempt_count) VALUES (?, 'UPDATED', ?, '{}', ?, ?, 2)",
                eventId,
                eventId,
                createdAt,
                parkedAt);
    }

    private String bootstrapWorkspaceApiKey(String workspaceSlug, String plaintextKey, String scopesJson) {
        Long workspaceId = jdbcTemplate.queryForObject("SELECT id FROM workspaces WHERE slug = ?", Long.class, workspaceSlug);
        jdbcTemplate.update(
                """
                INSERT INTO owner_api_keys (owner_id, workspace_id, key_prefix, key_hash, key_label, label, scopes_json, created_at, created_by)
                VALUES (1, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, 'test-bootstrap')
                """,
                workspaceId,
                plaintextKey,
                sha256(plaintextKey),
                plaintextKey,
                plaintextKey,
                scopesJson,
                OffsetDateTime.now());
        return plaintextKey;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte part : hash) {
                hex.append(String.format("%02x", part));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
