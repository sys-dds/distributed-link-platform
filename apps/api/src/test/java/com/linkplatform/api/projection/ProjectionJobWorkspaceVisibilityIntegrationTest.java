package com.linkplatform.api.projection;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
class ProjectionJobWorkspaceVisibilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("dataSource")
    private DataSource dataSource;

    @Test
    void projectionStoreOperatorCriticalMethodsDoNotUseInterfaceDefaultFallbacks() throws Exception {
        assertNotDefault("countQueued");
        assertNotDefault("countActive");
        assertNotDefault("countQueued", Long.class);
        assertNotDefault("countActive", Long.class);
        assertNotDefault("countFailed", Long.class);
        assertNotDefault("countCompleted", Long.class);
        assertNotDefault("findLatestStartedAt", Long.class);
        assertNotDefault("findLatestFailedAt", Long.class);
    }

    @Test
    void projectionJobsDefaultToActiveWorkspaceAndAreIsolatedAcrossWorkspaces() throws Exception {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createWorkspace("proj-a");
        createWorkspace("proj-b");
        String keyA = bootstrapWorkspaceApiKey("proj-a", "proj-a-ops", "[\"ops:read\",\"ops:write\"]");
        String keyB = bootstrapWorkspaceApiKey("proj-b", "proj-b-ops", "[\"ops:read\",\"ops:write\"]");

        String response = mockMvc.perform(post("/api/v1/projection-jobs")
                        .header("X-API-Key", keyA)
                        .header("X-Workspace-Slug", "proj-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobType\":\"CLICK_ROLLUP_REBUILD\",\"slug\":\"alpha\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceSlug").value("proj-a"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long jobId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).path("id").asLong();

        Long workspaceId = jdbcTemplate.queryForObject("SELECT id FROM workspaces WHERE slug = 'proj-a'", Long.class);
        Long storedWorkspaceId = jdbcTemplate.queryForObject("SELECT workspace_id FROM projection_jobs WHERE id = ?", Long.class, jobId);
        Long loggedWorkspaceId = jdbcTemplate.queryForObject(
                "SELECT workspace_id FROM operator_action_log WHERE action_type = 'projection_job_create' AND target_projection_job_id = ?",
                Long.class,
                jobId);
        org.junit.jupiter.api.Assertions.assertEquals(workspaceId, storedWorkspaceId);
        org.junit.jupiter.api.Assertions.assertEquals(workspaceId, loggedWorkspaceId);

        mockMvc.perform(get("/api/v1/projection-jobs/{id}", jobId)
                        .header("X-API-Key", keyA)
                        .header("X-Workspace-Slug", "proj-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceSlug").value("proj-a"));

        mockMvc.perform(get("/api/v1/projection-jobs/{id}", jobId)
                        .header("X-API-Key", keyB)
                        .header("X-Workspace-Slug", "proj-b"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/projection-jobs")
                        .header("X-API-Key", keyB)
                        .header("X-Workspace-Slug", "proj-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem((int) jobId))));
    }

    @Test
    void legacyNullWorkspaceJobsAreVisibleOnlyFromMatchingPersonalWorkspace() throws Exception {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        String personalWorkspaceSlug = jdbcTemplate.queryForObject(
                "SELECT slug FROM workspaces WHERE personal_workspace = TRUE AND created_by_owner_id = 1",
                String.class);

        jdbcTemplate.update(
                """
                INSERT INTO projection_jobs (job_type, status, requested_at, owner_id, requested_by_owner_id, slug)
                VALUES ('CLICK_ROLLUP_REBUILD', 'QUEUED', ?, 1, 1, 'legacy-visible'),
                       ('CLICK_ROLLUP_REBUILD', 'QUEUED', ?, 2, 2, 'legacy-hidden')
                """,
                OffsetDateTime.parse("2026-04-07T08:00:00Z"),
                OffsetDateTime.parse("2026-04-07T08:05:00Z"));

        Long visibleId = jdbcTemplate.queryForObject(
                "SELECT id FROM projection_jobs WHERE slug = 'legacy-visible'",
                Long.class);
        Long hiddenId = jdbcTemplate.queryForObject(
                "SELECT id FROM projection_jobs WHERE slug = 'legacy-hidden'",
                Long.class);

        mockMvc.perform(get("/api/v1/projection-jobs/{id}", visibleId)
                        .header("X-API-Key", "free-owner-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceSlug").value(personalWorkspaceSlug));

        mockMvc.perform(get("/api/v1/projection-jobs")
                        .header("X-API-Key", "free-owner-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug").value(org.hamcrest.Matchers.hasItem("legacy-visible")))
                .andExpect(jsonPath("$[*].slug").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("legacy-hidden"))));

        createWorkspace("team-x");
        String teamKey = bootstrapWorkspaceApiKey("team-x", "team-x-ops", "[\"ops:read\",\"ops:write\"]");
        mockMvc.perform(get("/api/v1/projection-jobs/{id}", visibleId)
                        .header("X-API-Key", teamKey)
                        .header("X-Workspace-Slug", "team-x"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/projection-jobs/{id}", hiddenId)
                        .header("X-API-Key", "free-owner-api-key"))
                .andExpect(status().isNotFound());
    }

    private void createWorkspace(String slug) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update(
                "INSERT INTO workspaces (slug, display_name, personal_workspace, created_at, created_by_owner_id) VALUES (?, ?, FALSE, ?, 1)",
                slug,
                slug,
                OffsetDateTime.now());
        Long workspaceId = jdbcTemplate.queryForObject("SELECT id FROM workspaces WHERE slug = ?", Long.class, slug);
        jdbcTemplate.update(
                "INSERT INTO workspace_members (workspace_id, owner_id, role, joined_at, added_by_owner_id, removed_at) VALUES (?, 1, 'OWNER', ?, 1, NULL)",
                workspaceId,
                OffsetDateTime.now());
    }

    private String bootstrapWorkspaceApiKey(String workspaceSlug, String plaintextKey, String scopesJson) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
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

    private void assertNotDefault(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = ProjectionJobStore.class.getMethod(methodName, parameterTypes);
        org.junit.jupiter.api.Assertions.assertFalse(method.isDefault(), methodName + " must not be a default interface method");
    }
}
