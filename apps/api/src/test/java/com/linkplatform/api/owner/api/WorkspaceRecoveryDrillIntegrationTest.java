package com.linkplatform.api.owner.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkplatform.api.owner.application.WorkspaceExportRunner;
import com.linkplatform.api.owner.application.WorkspaceRecoveryDrillRunner;
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
class WorkspaceRecoveryDrillIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WorkspaceExportRunner workspaceExportRunner;

    @Autowired
    private WorkspaceRecoveryDrillRunner workspaceRecoveryDrillRunner;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void recoveryDrillsQueueCompleteSummarizeAndKeepShadowValidationReadOnly() throws Exception {
        createWorkspace("recovery-drill");
        String apiKey = bootstrapWorkspaceApiKey("recovery-drill", "recovery-drill-key");

        mockMvc.perform(post("/api/v1/links")
                        .header("X-API-Key", apiKey)
                        .header("X-Workspace-Slug", "recovery-drill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"recovery-link\",\"originalUrl\":\"https://example.com/recovery\"}"))
                .andExpect(status().isCreated());

        String exportResponse = mockMvc.perform(post("/api/v1/workspaces/current/exports")
                        .header("X-API-Key", apiKey)
                        .header("X-Workspace-Slug", "recovery-drill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"includeClicks\":false,\"includeSecurityEvents\":false,\"includeAbuseCases\":false,\"includeWebhooks\":false}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long exportId = objectMapper.readTree(exportResponse).path("id").asLong();
        workspaceExportRunner.runQueuedExports();

        String created = mockMvc.perform(post("/api/v1/workspaces/current/recovery-drills")
                        .header("X-API-Key", apiKey)
                        .header("X-Workspace-Slug", "recovery-drill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceExportId":%d,"targetMode":"SHADOW_VALIDATE","dryRun":true}
                                """.formatted(exportId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long drillId = objectMapper.readTree(created).path("id").asLong();

        workspaceRecoveryDrillRunner.runQueuedRecoveryDrills();

        mockMvc.perform(get("/api/v1/workspaces/current/recovery-drills/{drillId}", drillId)
                        .header("X-API-Key", apiKey)
                        .header("X-Workspace-Slug", "recovery-drill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.summary.mutatedData").value(false))
                .andExpect(jsonPath("$.summary.estimatedRestoreCounts.links").value(1));
    }

    private void createWorkspace(String slug) throws Exception {
        mockMvc.perform(post("/api/v1/workspaces")
                        .header("X-API-Key", "free-owner-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"%s\",\"displayName\":\"%s\"}".formatted(slug, slug)))
                .andExpect(status().isCreated());
    }

    private String bootstrapWorkspaceApiKey(String workspaceSlug, String plaintextKey) {
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
                "[\"exports:read\",\"exports:write\",\"links:read\",\"links:write\"]",
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
