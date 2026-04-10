package com.linkplatform.api.owner.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkplatform.api.owner.application.WorkspaceImportRunner;
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
class WorkspaceImportRestoreIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkspaceImportRunner workspaceImportRunner;

    @Test
    void dryRunProducesReadyToApplyAndApplyRestoresSafeDataWithoutSecrets() throws Exception {
        createWorkspace("team-import");
        String apiKey = bootstrapWorkspaceApiKey("team-import", "team-import-key");

        String response = mockMvc.perform(post("/api/v1/workspaces/current/imports")
                        .header("X-API-Key", apiKey)
                        .header("X-Workspace-Slug", "team-import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importRequest(false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long importId = objectMapper.readTree(response).path("id").asLong();

        workspaceImportRunner.runQueuedImports();

        mockMvc.perform(get("/api/v1/workspaces/current/imports/{importId}", importId)
                        .header("X-API-Key", apiKey)
                        .header("X-Workspace-Slug", "team-import"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_TO_APPLY"))
                .andExpect(jsonPath("$.summary.linksCreated").value(1))
                .andExpect(jsonPath("$.summary.linksUpdated").value(0))
                .andExpect(jsonPath("$.summary.conflicts").value(0));

        mockMvc.perform(post("/api/v1/workspaces/current/imports/{importId}/apply", importId)
                        .header("X-API-Key", apiKey)
                        .header("X-Workspace-Slug", "team-import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(get("/api/v1/links/{slug}", "restored-link")
                        .header("X-API-Key", apiKey)
                        .header("X-Workspace-Slug", "team-import"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("restored-link"))
                .andExpect(jsonPath("$.title").value("Restored title"))
                .andExpect(jsonPath("$.tags").isArray());

        String lifecycle = jdbcTemplate.queryForObject(
                "SELECT lifecycle_state FROM links WHERE workspace_id = (SELECT id FROM workspaces WHERE slug = 'team-import') AND slug = 'restored-link'",
                String.class);
        org.assertj.core.api.Assertions.assertThat(lifecycle).isEqualTo("ACTIVE");

        String secretHash = jdbcTemplate.queryForObject(
                "SELECT signing_secret_hash FROM webhook_subscriptions WHERE workspace_id = (SELECT id FROM workspaces WHERE slug = 'team-import') AND name = 'Imported Hook'",
                String.class);
        org.assertj.core.api.Assertions.assertThat(secretHash).isEqualTo("imported-disabled");
        OffsetDateTime completedAt = jdbcTemplate.queryForObject(
                "SELECT completed_at FROM workspace_import_jobs WHERE id = ?",
                OffsetDateTime.class,
                importId);
        org.assertj.core.api.Assertions.assertThat(completedAt).isNotNull();
    }

    @Test
    void overwriteConflictsControlsWhetherExistingLinkIsUpdated() throws Exception {
        createWorkspace("team-import-conflict");
        String apiKey = bootstrapWorkspaceApiKey("team-import-conflict", "team-import-conflict-key");
        createLink(apiKey, "team-import-conflict", "conflict-link", "https://example.com/original");

        String first = mockMvc.perform(post("/api/v1/workspaces/current/imports")
                        .header("X-API-Key", apiKey)
                        .header("X-Workspace-Slug", "team-import-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importRequest(false).replace("\"restored-link\"", "\"conflict-link\"")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        workspaceImportRunner.runQueuedImports();
        long firstImportId = objectMapper.readTree(first).path("id").asLong();

        mockMvc.perform(get("/api/v1/workspaces/current/imports/{importId}", firstImportId)
                        .header("X-API-Key", apiKey)
                        .header("X-Workspace-Slug", "team-import-conflict"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.conflicts").value(1));

        String second = mockMvc.perform(post("/api/v1/workspaces/current/imports")
                        .header("X-API-Key", apiKey)
                        .header("X-Workspace-Slug", "team-import-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importRequest(true).replace("\"restored-link\"", "\"conflict-link\"")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        workspaceImportRunner.runQueuedImports();
        long secondImportId = objectMapper.readTree(second).path("id").asLong();

        mockMvc.perform(post("/api/v1/workspaces/current/imports/{importId}/apply", secondImportId)
                        .header("X-API-Key", apiKey)
                        .header("X-Workspace-Slug", "team-import-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        String updatedUrl = jdbcTemplate.queryForObject(
                "SELECT original_url FROM links WHERE workspace_id = (SELECT id FROM workspaces WHERE slug = 'team-import-conflict') AND slug = 'conflict-link'",
                String.class);
        org.assertj.core.api.Assertions.assertThat(updatedUrl).isEqualTo("https://example.com/restored");
    }

    private String importRequest(boolean overwriteConflicts) {
        return """
                {
                  "payloadJson":{
                    "links":[
                      {
                        "slug":"restored-link",
                        "originalUrl":"https://example.com/restored",
                        "title":"Restored title",
                        "tags":["alpha","beta"],
                        "lifecycleState":"ARCHIVED"
                      }
                    ],
                    "webhooksIncluded":true,
                    "webhooks":[
                      {
                        "name":"Imported Hook",
                        "callbackUrl":"https://hooks.example.com/imported",
                        "eventTypes":["link.created"],
                        "secret":"plaintext-should-not-restore"
                      }
                    ]
                  },
                  "dryRun":true,
                  "overwriteConflicts":%s
                }
                """.formatted(overwriteConflicts);
    }

    private void createWorkspace(String slug) throws Exception {
        mockMvc.perform(post("/api/v1/workspaces")
                        .header("X-API-Key", "free-owner-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"%s","displayName":"%s"}
                                """.formatted(slug, slug)))
                .andExpect(status().isCreated());
    }

    private void createLink(String apiKey, String workspaceSlug, String slug, String originalUrl) throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .header("X-API-Key", apiKey)
                        .header("X-Workspace-Slug", workspaceSlug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"%s","originalUrl":"%s"}
                                """.formatted(slug, originalUrl)))
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
                "[\"exports:read\",\"exports:write\",\"links:read\",\"links:write\",\"webhooks:read\",\"webhooks:write\"]",
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
