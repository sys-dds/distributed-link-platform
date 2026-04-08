package com.linkplatform.api.owner.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class WorkspacePlanControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void planAndUsageReadsRequireMembersRead() throws Exception {
        String membersReadKey = bootstrapPersonalWorkspaceApiKey("members-read-key", "[\"members:read\"]");
        String retentionReadKey = bootstrapPersonalWorkspaceApiKey("retention-read-key", "[\"retention:read\"]");
        String linksReadKey = bootstrapPersonalWorkspaceApiKey("links-read-key", "[\"links:read\"]");

        mockMvc.perform(get("/api/v1/workspaces/current/plan").header("X-API-Key", membersReadKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceSlug").value("free-owner"));
        mockMvc.perform(get("/api/v1/workspaces/current/usage").header("X-API-Key", membersReadKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceSlug").value("free-owner"));

        mockMvc.perform(get("/api/v1/workspaces/current/plan").header("X-API-Key", retentionReadKey))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/workspaces/current/usage").header("X-API-Key", retentionReadKey))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/workspaces/current/plan").header("X-API-Key", linksReadKey))
                .andExpect(status().isForbidden());
    }

    @Test
    void retentionReadAndWriteUseExplicitScopes() throws Exception {
        String retentionReadKey = bootstrapPersonalWorkspaceApiKey("retention-read-only", "[\"retention:read\"]");
        String retentionWriteKey = bootstrapPersonalWorkspaceApiKey("retention-write-key", "[\"retention:write\"]");

        mockMvc.perform(get("/api/v1/workspaces/current/retention").header("X-API-Key", retentionReadKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickHistoryDays").isNumber());

        mockMvc.perform(patch("/api/v1/workspaces/current/retention")
                        .header("X-API-Key", retentionReadKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clickHistoryDays":30,"securityEventsDays":30,"webhookDeliveriesDays":30,"abuseCasesDays":30,"operatorActionLogDays":30}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/workspaces/current/retention")
                        .header("X-API-Key", retentionWriteKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clickHistoryDays":30,"securityEventsDays":30,"webhookDeliveriesDays":30,"abuseCasesDays":30,"operatorActionLogDays":30}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickHistoryDays").value(30));
    }

    @Test
    void opsPlanUpdateStillRequiresOpsWrite() throws Exception {
        String membersReadKey = bootstrapPersonalWorkspaceApiKey("members-only-plan", "[\"members:read\"]");
        String opsWriteKey = bootstrapPersonalWorkspaceApiKey("ops-write-plan", "[\"ops:write\"]");

        mockMvc.perform(patch("/api/v1/ops/workspaces/{workspaceSlug}/plan", "free-owner")
                        .header("X-API-Key", membersReadKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"planCode":"PRO"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/ops/workspaces/{workspaceSlug}/plan", "free-owner")
                        .header("X-API-Key", opsWriteKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"planCode":"PRO"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode").value("PRO"));
    }

    @Test
    void ownerAndAdminCanReadManagementStateButEditorAndViewerCannot() throws Exception {
        Long workspaceId = createWorkspace("team-plan");
        ensureOwner(3L, "plan-admin");
        ensureOwner(4L, "plan-editor");
        ensureOwner(5L, "plan-viewer");
        addMember(workspaceId, 1L, "OWNER");
        addMember(workspaceId, 3L, "ADMIN");
        addMember(workspaceId, 4L, "EDITOR");
        addMember(workspaceId, 5L, "VIEWER");

        String ownerKey = bootstrapWorkspaceApiKey(workspaceId, 1L, "team-plan-owner", "[\"members:read\",\"retention:read\"]");
        String adminKey = bootstrapWorkspaceApiKey(workspaceId, 3L, "team-plan-admin", "[\"members:read\",\"retention:read\"]");
        String editorKey = bootstrapWorkspaceApiKey(workspaceId, 4L, "team-plan-editor", "[\"members:read\",\"retention:read\"]");
        String viewerKey = bootstrapWorkspaceApiKey(workspaceId, 5L, "team-plan-viewer", "[\"members:read\",\"retention:read\"]");

        mockMvc.perform(get("/api/v1/workspaces/current/plan")
                        .header("X-API-Key", ownerKey)
                        .header("X-Workspace-Slug", "team-plan"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/workspaces/current/usage")
                        .header("X-API-Key", adminKey)
                        .header("X-Workspace-Slug", "team-plan"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/workspaces/current/retention")
                        .header("X-API-Key", adminKey)
                        .header("X-Workspace-Slug", "team-plan"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/workspaces/current/plan")
                        .header("X-API-Key", editorKey)
                        .header("X-Workspace-Slug", "team-plan"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/workspaces/current/usage")
                        .header("X-API-Key", editorKey)
                        .header("X-Workspace-Slug", "team-plan"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/workspaces/current/retention")
                        .header("X-API-Key", viewerKey)
                        .header("X-Workspace-Slug", "team-plan"))
                .andExpect(status().isForbidden());
    }

    private String bootstrapPersonalWorkspaceApiKey(String plaintextKey, String scopesJson) {
        Long workspaceId = jdbcTemplate.queryForObject(
                "SELECT id FROM workspaces WHERE slug = 'free-owner'",
                Long.class);
        bootstrapWorkspaceApiKey(workspaceId, 1L, plaintextKey, scopesJson);
        return plaintextKey;
    }

    private String bootstrapWorkspaceApiKey(Long workspaceId, long ownerId, String plaintextKey, String scopesJson) {
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

    private Long createWorkspace(String slug) {
        jdbcTemplate.update(
                "INSERT INTO workspaces (slug, display_name, personal_workspace, created_at, created_by_owner_id) VALUES (?, ?, FALSE, ?, 1)",
                slug,
                slug,
                OffsetDateTime.now());
        return jdbcTemplate.queryForObject("SELECT id FROM workspaces WHERE slug = ?", Long.class, slug);
    }

    private void addMember(Long workspaceId, long ownerId, String role) {
        jdbcTemplate.update(
                "INSERT INTO workspace_members (workspace_id, owner_id, role, joined_at, added_by_owner_id, removed_at) VALUES (?, ?, ?, ?, 1, NULL)",
                workspaceId,
                ownerId,
                role,
                OffsetDateTime.now());
    }

    private void ensureOwner(long ownerId, String ownerKey) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM owners WHERE id = ?", Integer.class, ownerId);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO owners (id, owner_key, display_name, plan, created_at) VALUES (?, ?, ?, 'FREE', ?)",
                ownerId,
                ownerKey,
                ownerKey,
                OffsetDateTime.now());
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
