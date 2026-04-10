package com.linkplatform.api.owner.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class WorkspaceEnterprisePolicyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void expiryPolicyDualControlAndServiceAccountApprovalRestrictionWork() throws Exception {
        createWorkspace("enterprise-policy");
        Long workspaceId = jdbcTemplate.queryForObject("SELECT id FROM workspaces WHERE slug = 'enterprise-policy'", Long.class);
        addHumanMember(workspaceId, 2);
        String initiatorKey = bootstrapWorkspaceApiKey(workspaceId, 1, "enterprise-policy-initiator", "[\"members:read\",\"members:write\",\"api_keys:write\",\"ops:write\"]");
        String approverKey = bootstrapWorkspaceApiKey(workspaceId, 2, "enterprise-policy-approver", "[\"members:read\",\"members:write\"]");
        String serviceKey = bootstrapServiceAccountKey(workspaceId, "enterprise-policy-service", "[\"members:write\"]");

        mockMvc.perform(patch("/api/v1/workspaces/current/enterprise-policy")
                        .header("X-API-Key", initiatorKey)
                        .header("X-Workspace-Slug", "enterprise-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requireApiKeyExpiry": true,
                                  "maxApiKeyTtlDays": 1,
                                  "requireDualControlForPlanChanges": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requireApiKeyExpiry").value(true))
                .andExpect(jsonPath("$.requireDualControlForPlanChanges").value(true));

        mockMvc.perform(post("/api/v1/owner/api-keys")
                        .header("X-API-Key", initiatorKey)
                        .header("X-Workspace-Slug", "enterprise-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"no-expiry\",\"scopes\":[\"links:read\"]}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/owner/api-keys")
                        .header("X-API-Key", initiatorKey)
                        .header("X-Workspace-Slug", "enterprise-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"with-expiry","expiresAt":"%s","scopes":["links:read"]}
                                """.formatted(OffsetDateTime.now().plusHours(12))))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/v1/ops/workspaces/enterprise-policy/plan")
                        .header("X-API-Key", initiatorKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"PRO\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/workspaces/current/privileged-actions/workspace_plan_update/approve")
                        .header("X-API-Key", serviceKey)
                        .header("X-Workspace-Slug", "enterprise-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initiatorOwnerId\":1}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/workspaces/current/privileged-actions/workspace_plan_update/approve")
                        .header("X-API-Key", approverKey)
                        .header("X-Workspace-Slug", "enterprise-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initiatorOwnerId\":1}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/ops/workspaces/enterprise-policy/plan")
                        .header("X-API-Key", initiatorKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"PRO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode").value("PRO"));
    }

    private void createWorkspace(String slug) throws Exception {
        mockMvc.perform(post("/api/v1/workspaces")
                        .header("X-API-Key", "free-owner-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"%s\",\"displayName\":\"%s\"}".formatted(slug, slug)))
                .andExpect(status().isCreated());
    }

    private void addHumanMember(long workspaceId, long ownerId) {
        jdbcTemplate.update(
                "INSERT INTO workspace_members (workspace_id, owner_id, role, joined_at, added_by_owner_id) VALUES (?, ?, 'OWNER', ?, 1)",
                workspaceId,
                ownerId,
                OffsetDateTime.now());
    }

    private String bootstrapServiceAccountKey(long workspaceId, String plaintextKey, String scopesJson) {
        jdbcTemplate.update(
                "INSERT INTO owners (id, owner_key, display_name, plan, created_at) VALUES (90, ?, 'Enterprise Bot', 'FREE', ?)",
                "svc-" + workspaceId + "-enterprise-bot",
                OffsetDateTime.now());
        jdbcTemplate.update(
                "INSERT INTO workspace_members (workspace_id, owner_id, role, joined_at, added_by_owner_id, member_type) VALUES (?, 90, 'ADMIN', ?, 1, 'SERVICE_ACCOUNT')",
                workspaceId,
                OffsetDateTime.now());
        return bootstrapWorkspaceApiKey(workspaceId, 90, plaintextKey, scopesJson);
    }

    private String bootstrapWorkspaceApiKey(long workspaceId, long ownerId, String plaintextKey, String scopesJson) {
        jdbcTemplate.update(
                """
                INSERT INTO owner_api_keys (owner_id, workspace_id, key_prefix, key_hash, key_label, label, scopes_json, created_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, 'test-bootstrap')
                """,
                ownerId,
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
