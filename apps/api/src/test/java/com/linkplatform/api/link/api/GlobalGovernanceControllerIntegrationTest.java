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
class GlobalGovernanceControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void opsReadCanFetchGlobalGovernanceViewsAndNonOpsCallerIsDenied() throws Exception {
        long workspaceId = createWorkspace("governance-a");
        long otherWorkspaceId = createWorkspace("governance-b");
        String opsKey = bootstrapWorkspaceApiKey(workspaceId, "governance-ops", "[\"ops:read\"]");
        String nonOpsKey = bootstrapWorkspaceApiKey(workspaceId, "governance-non-ops", "[\"links:read\"]");

        createFailingWebhook(workspaceId);
        createOpenAbuseCase(workspaceId);
        jdbcTemplate.update(
                "UPDATE workspace_plans SET active_links_limit = 0 WHERE workspace_id = ?",
                otherWorkspaceId);
        jdbcTemplate.update(
                """
                INSERT INTO links (slug, original_url, created_at, hostname, version, owner_id, workspace_id, lifecycle_state, abuse_status)
                VALUES ('governance-over', 'https://over.example/a', ?, 'over.example', 1, 1, ?, 'ACTIVE', 'ACTIVE')
                """,
                OffsetDateTime.now(),
                otherWorkspaceId);

        mockMvc.perform(get("/api/v1/ops/governance/summary")
                        .header("X-API-Key", nonOpsKey)
                        .header("X-Workspace-Slug", "governance-a"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/ops/governance/summary")
                        .header("X-API-Key", opsKey)
                        .header("X-Workspace-Slug", "governance-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalWorkspaces").isNumber())
                .andExpect(jsonPath("$.generatedAt").isNotEmpty())
                .andExpect(jsonPath("$.totalFailingWebhookSubscriptions").value(1))
                .andExpect(jsonPath("$.totalOpenAbuseCases").value(1))
                .andExpect(jsonPath("$.totalOverQuotaWorkspaces").value(1));

        mockMvc.perform(get("/api/v1/ops/governance/webhooks/risk")
                        .header("X-API-Key", opsKey)
                        .header("X-Workspace-Slug", "governance-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].workspaceSlug").value("governance-a"))
                .andExpect(jsonPath("$.items[0].consecutiveFailures").value(7));

        mockMvc.perform(get("/api/v1/ops/governance/abuse/risk")
                        .header("X-API-Key", opsKey)
                        .header("X-Workspace-Slug", "governance-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].host").value("bad.example"))
                .andExpect(jsonPath("$.items[0].signalCount").value(5));

        mockMvc.perform(get("/api/v1/ops/governance/over-quota")
                        .header("X-API-Key", opsKey)
                        .header("X-Workspace-Slug", "governance-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].workspaceSlug").value("governance-b"))
                .andExpect(jsonPath("$.items[0].metric").value("ACTIVE_LINKS"));
    }

    private void createFailingWebhook(long workspaceId) {
        jdbcTemplate.update(
                """
                INSERT INTO webhook_subscriptions (
                    workspace_id, name, callback_url, signing_secret_hash, signing_secret_prefix,
                    enabled, event_types_json, created_at, updated_at, consecutive_failures, last_failure_at
                ) VALUES (?, 'risk hook', 'https://example.com/hook', 'hash', 'hash', TRUE, CAST('[\"link.created\"]' AS jsonb), ?, ?, 7, ?)
                """,
                workspaceId,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.parse("2026-04-08T08:00:00Z"));
    }

    private void createOpenAbuseCase(long workspaceId) {
        jdbcTemplate.update(
                """
                INSERT INTO links (slug, original_url, created_at, hostname, version, owner_id, workspace_id, lifecycle_state, abuse_status)
                VALUES ('governance-bad', 'https://bad.example/a', ?, 'bad.example', 1, 1, ?, 'ACTIVE', 'QUARANTINED')
                """,
                OffsetDateTime.now(),
                workspaceId);
        jdbcTemplate.update(
                """
                INSERT INTO link_abuse_cases (
                    workspace_id, slug, status, source, signal_count, risk_score,
                    summary, target_host, created_at, updated_at
                ) VALUES (?, 'governance-bad', 'OPEN', 'MANUAL_OPERATOR', 5, 90, 'bad host', 'bad.example', ?, ?)
                """,
                workspaceId,
                OffsetDateTime.now(),
                OffsetDateTime.now());
    }

    private long createWorkspace(String slug) {
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
        jdbcTemplate.update(
                """
                INSERT INTO workspace_plans (
                    workspace_id, plan_code, active_links_limit, members_limit, api_keys_limit,
                    webhooks_limit, monthly_webhook_deliveries_limit, exports_enabled, created_at, updated_at
                ) VALUES (?, 'FREE', 100, 5, 10, 5, 10000, TRUE, ?, ?)
                """,
                workspaceId,
                OffsetDateTime.now(),
                OffsetDateTime.now());
        return workspaceId;
    }

    private String bootstrapWorkspaceApiKey(long workspaceId, String plaintextKey, String scopesJson) {
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
