package com.linkplatform.api.link.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class OperatorActionLogControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void actionHistoryIsLoggedPaginatedAndWorkspaceScoped() throws Exception {
        createWorkspace("ops-log");
        createWorkspace("ops-log-other");
        String opsKey = bootstrapWorkspaceApiKey("ops-log", "ops-log-key", "[\"ops:read\",\"ops:write\",\"links:read\",\"links:write\"]");
        String otherOpsKey = bootstrapWorkspaceApiKey("ops-log-other", "ops-log-other-key", "[\"ops:read\",\"ops:write\",\"links:read\",\"links:write\"]");

        createLink("ops-log", opsKey, "log-link", "https://example.com/log-link");
        createLink("ops-log-other", otherOpsKey, "other-link", "https://example.com/other-link");

        mockMvc.perform(post("/api/v1/analytics/pipeline/pause")
                        .header("X-API-Key", opsKey)
                        .header("X-Workspace-Slug", "ops-log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"maintenance\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/ops/abuse/reviews/manual")
                        .header("X-API-Key", opsKey)
                        .header("X-Workspace-Slug", "ops-log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"log-link\",\"summary\":\"manual review\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/projection-jobs")
                        .header("X-API-Key", opsKey)
                        .header("X-Workspace-Slug", "ops-log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobType\":\"CLICK_ROLLUP_REBUILD\",\"slug\":\"log-link\",\"operatorNote\":\"repair window\"}"))
                .andExpect(status().isOk());

        Long opsWorkspaceId = jdbcTemplate.queryForObject("SELECT id FROM workspaces WHERE slug = 'ops-log'", Long.class);
        Long loggedProjectionWorkspaceId = jdbcTemplate.queryForObject(
                "SELECT workspace_id FROM operator_action_log WHERE action_type = 'projection_job_create' ORDER BY id DESC LIMIT 1",
                Long.class);
        org.junit.jupiter.api.Assertions.assertEquals(opsWorkspaceId, loggedProjectionWorkspaceId);

        mockMvc.perform(post("/api/v1/analytics/pipeline/resume")
                        .header("X-API-Key", otherOpsKey)
                        .header("X-Workspace-Slug", "ops-log-other"))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/v1/ops/actions")
                        .header("X-API-Key", opsKey)
                        .header("X-Workspace-Slug", "ops-log")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].subsystem").isNotEmpty())
                .andExpect(jsonPath("$.items[*].actionType").value(org.hamcrest.Matchers.hasItems("projection_job_create", "abuse_manual_case_create")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String nextCursor = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).path("nextCursor").asText();
        mockMvc.perform(get("/api/v1/ops/actions")
                        .header("X-API-Key", opsKey)
                        .header("X-Workspace-Slug", "ops-log")
                        .param("limit", "2")
                        .param("cursor", nextCursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].actionType").value("analytics_pipeline_pause"))
                .andExpect(jsonPath("$.items[0].note").value("maintenance"));

        mockMvc.perform(get("/api/v1/ops/actions")
                        .header("X-API-Key", opsKey)
                        .header("X-Workspace-Slug", "ops-log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].note").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("other")))));
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

    private void createLink(String workspaceSlug, String apiKey, String slug, String originalUrl) throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .header("X-API-Key", apiKey)
                        .header("X-Workspace-Slug", workspaceSlug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"%s\",\"originalUrl\":\"%s\"}".formatted(slug, originalUrl)))
                .andExpect(status().isCreated());
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
