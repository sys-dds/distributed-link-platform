package com.linkplatform.api.projection;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ScopedProjectionJobRangeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void scopedProjectionJobStoresWorkspaceSlugRangeAndOperatorNote() throws Exception {
        createWorkspace("repair");
        String opsKey = bootstrapWorkspaceApiKey("repair", "repair-ops", "[\"ops:read\",\"ops:write\",\"links:read\",\"links:write\"]");

        mockMvc.perform(post("/api/v1/links")
                        .header("X-API-Key", opsKey)
                        .header("X-Workspace-Slug", "repair")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"scoped-link\",\"originalUrl\":\"https://example.com/scoped-link\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/projection-jobs")
                        .header("X-API-Key", opsKey)
                        .header("X-Workspace-Slug", "repair")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobType":"CLICK_ROLLUP_REBUILD","workspaceSlug":"repair","slug":"scoped-link","from":"2026-04-01T00:00:00Z","to":"2026-04-02T00:00:00Z","operatorNote":" targeted repair "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceSlug").value("repair"))
                .andExpect(jsonPath("$.slug").value("scoped-link"))
                .andExpect(jsonPath("$.from").value("2026-04-01T00:00:00Z"))
                .andExpect(jsonPath("$.to").value("2026-04-02T00:00:00Z"))
                .andExpect(jsonPath("$.requestedByOwnerId").value(1))
                .andExpect(jsonPath("$.operatorNote").value("targeted repair"));

        Long workspaceId = jdbcTemplate.queryForObject("SELECT id FROM workspaces WHERE slug = 'repair'", Long.class);
        Long loggedWorkspaceId = jdbcTemplate.queryForObject(
                "SELECT workspace_id FROM operator_action_log WHERE action_type = 'projection_job_create' ORDER BY id DESC LIMIT 1",
                Long.class);
        org.junit.jupiter.api.Assertions.assertEquals(workspaceId, loggedWorkspaceId);

        mockMvc.perform(post("/api/v1/projection-jobs")
                        .header("X-API-Key", opsKey)
                        .header("X-Workspace-Slug", "repair")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobType":"CLICK_ROLLUP_REBUILD","workspaceSlug":"   ","slug":"scoped-link","operatorNote":"blank scope"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceSlug").value("repair"))
                .andExpect(jsonPath("$.slug").value("scoped-link"));

        mockMvc.perform(post("/api/v1/projection-jobs")
                        .header("X-API-Key", opsKey)
                        .header("X-Workspace-Slug", "repair")
                        .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"jobType":"LINK_CATALOG_REBUILD","workspaceSlug":"repair","slug":"scoped-link","from":"2026-04-01T00:00:00Z","to":"2026-04-02T00:00:00Z"}
                                """))
                .andExpect(status().isBadRequest());
    }

    private void createWorkspace(String slug) {
        jdbcTemplate.update(
                "INSERT INTO workspaces (slug, display_name, personal_workspace, created_at, created_by_owner_id) VALUES (?, ?, FALSE, ?, 1)",
                slug,
                slug,
                OffsetDateTime.now());
        Long workspaceId = jdbcTemplate.queryForObject("SELECT id FROM workspaces WHERE slug = ?", Long.class, slug);
        jdbcTemplate.update(
                "INSERT INTO workspace_memberships (workspace_id, owner_id, role, joined_at, added_by_owner_id) VALUES (?, 1, 'OWNER', ?, 1)",
                workspaceId,
                OffsetDateTime.now());
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
