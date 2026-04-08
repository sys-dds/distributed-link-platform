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
class AbuseReviewControllerIntegrationTest {

    private static final String FREE_API_KEY = "free-owner-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void opsReadAndWriteAreWorkspaceScopedAndCursorPaginationIsStable() throws Exception {
        createWorkspace("abuse-team");
        String opsReadKey = bootstrapWorkspaceApiKey("abuse-team", "abuse-read-key", "[\"ops:read\"]");
        String opsWriteKey = bootstrapWorkspaceApiKey("abuse-team", "abuse-write-key", "[\"ops:write\",\"links:read\",\"links:write\"]");
        String linksOnlyKey = bootstrapWorkspaceApiKey("abuse-team", "links-only-key", "[\"links:read\"]");

        createLink("abuse-team", opsWriteKey, "review-a", "https://example.com/a");
        createLink("abuse-team", opsWriteKey, "review-b", "https://example.com/b");
        createLink("abuse-team", opsWriteKey, "review-c", "https://example.com/c");

        createManualCase("abuse-team", opsWriteKey, "review-a", false);
        Thread.sleep(5L);
        createManualCase("abuse-team", opsWriteKey, "review-b", false);
        Thread.sleep(5L);
        createManualCase("abuse-team", opsWriteKey, "review-c", false);

        createWorkspace("other-team");
        String otherOpsWriteKey = bootstrapWorkspaceApiKey("other-team", "other-write-key", "[\"ops:write\",\"links:read\",\"links:write\"]");
        createLink("other-team", otherOpsWriteKey, "other-review", "https://example.com/other");
        createManualCase("other-team", otherOpsWriteKey, "other-review", false);

        mockMvc.perform(get("/api/v1/ops/abuse/reviews")
                        .header("X-API-Key", linksOnlyKey)
                        .header("X-Workspace-Slug", "abuse-team"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.category").value("scope-denied"));

        String cursor = mockMvc.perform(get("/api/v1/ops/abuse/reviews")
                        .header("X-API-Key", opsReadKey)
                        .header("X-Workspace-Slug", "abuse-team")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].slug").value("review-c"))
                .andExpect(jsonPath("$.items[1].slug").value("review-b"))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String nextCursor = new com.fasterxml.jackson.databind.ObjectMapper().readTree(cursor).path("nextCursor").asText();

        mockMvc.perform(get("/api/v1/ops/abuse/reviews")
                        .header("X-API-Key", opsReadKey)
                        .header("X-Workspace-Slug", "abuse-team")
                        .param("limit", "2")
                        .param("cursor", nextCursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("review-a"))
                .andExpect(jsonPath("$.hasMore").value(false));

        mockMvc.perform(get("/api/v1/ops/abuse/reviews")
                        .header("X-API-Key", opsReadKey)
                        .header("X-Workspace-Slug", "abuse-team"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].slug").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("other-review"))));

        mockMvc.perform(post("/api/v1/ops/abuse/reviews/manual")
                        .header("X-API-Key", opsWriteKey)
                        .header("X-Workspace-Slug", "abuse-team")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"review-a","summary":"manual review"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("manual_operator"));

        mockMvc.perform(get("/api/v1/workspaces/current/abuse/trends")
                        .header("X-API-Key", opsReadKey)
                        .header("X-Workspace-Slug", "abuse-team"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOpenAbuseCases").value(3));
    }

    @Test
    void opsWriteCanQuarantineReleaseAndDismiss() throws Exception {
        createWorkspace("abuse-actions");
        String opsWriteKey = bootstrapWorkspaceApiKey("abuse-actions", "abuse-actions-key", "[\"ops:write\",\"ops:read\",\"links:read\",\"links:write\"]");

        createLink("abuse-actions", opsWriteKey, "case-link", "https://example.com/case-link");

        long caseId = createManualCase("abuse-actions", opsWriteKey, "case-link", false);

        mockMvc.perform(post("/api/v1/ops/abuse/reviews/{caseId}/quarantine", caseId)
                        .header("X-API-Key", opsWriteKey)
                        .header("X-Workspace-Slug", "abuse-actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resolutionNote":"confirmed abuse"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("quarantined"))
                .andExpect(jsonPath("$.resolution").value("quarantine"));

        mockMvc.perform(post("/api/v1/ops/abuse/reviews/manual")
                        .header("X-API-Key", opsWriteKey)
                        .header("X-Workspace-Slug", "abuse-actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"case-link","summary":"secondary review"}
                                """))
                .andExpect(status().isOk());

        Long dismissCaseId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM link_abuse_cases WHERE workspace_id = (SELECT id FROM workspaces WHERE slug = 'abuse-actions') AND source = 'MANUAL_OPERATOR' AND status = 'OPEN'",
                Long.class);

        mockMvc.perform(post("/api/v1/ops/abuse/reviews/{caseId}/dismiss", dismissCaseId)
                        .header("X-API-Key", opsWriteKey)
                        .header("X-Workspace-Slug", "abuse-actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resolutionNote":"not this signal"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("dismissed"));

        mockMvc.perform(get("/case-link"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/ops/abuse/reviews/{caseId}/release", caseId)
                        .header("X-API-Key", opsWriteKey)
                        .header("X-Workspace-Slug", "abuse-actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resolutionNote":"released"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("released"))
                .andExpect(jsonPath("$.resolution").value("release"));

        Integer operatorActions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operator_action_log WHERE subsystem = 'ABUSE' AND action_type IN ('abuse_manual_case_create', 'abuse_quarantine', 'abuse_dismiss', 'abuse_release')",
                Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(4, operatorActions);
    }

    private void createWorkspace(String slug) throws Exception {
        mockMvc.perform(post("/api/v1/workspaces")
                        .header("X-API-Key", FREE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"%s","displayName":"%s"}
                                """.formatted(slug, slug)))
                .andExpect(status().isCreated());
    }

    private void createLink(String workspaceSlug, String apiKey, String slug, String originalUrl) throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .header("X-API-Key", apiKey)
                        .header("X-Workspace-Slug", workspaceSlug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"%s","originalUrl":"%s"}
                                """.formatted(slug, originalUrl)))
                .andExpect(status().isCreated());
    }

    private long createManualCase(String workspaceSlug, String apiKey, String slug, boolean quarantineNow) throws Exception {
        mockMvc.perform(post("/api/v1/ops/abuse/reviews/manual")
                        .header("X-API-Key", apiKey)
                        .header("X-Workspace-Slug", workspaceSlug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"%s","summary":"needs review","quarantineNow":%s}
                                """.formatted(slug, quarantineNow)))
                .andExpect(status().isOk());
        Long caseId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM link_abuse_cases WHERE workspace_id = (SELECT id FROM workspaces WHERE slug = ?) AND slug = ?",
                Long.class,
                workspaceSlug,
                slug);
        return caseId == null ? -1L : caseId;
    }

    private String bootstrapWorkspaceApiKey(String workspaceSlug, String plaintextKey, String scopesJson) {
        Long workspaceId = jdbcTemplate.queryForObject("SELECT id FROM workspaces WHERE slug = ?", Long.class, workspaceSlug);
        jdbcTemplate.update(
                """
                INSERT INTO owner_api_keys (
                    owner_id, workspace_id, key_prefix, key_hash, key_label, label, scopes_json, created_at, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
                """,
                1L,
                workspaceId,
                plaintextKey,
                sha256(plaintextKey),
                plaintextKey,
                plaintextKey,
                scopesJson,
                OffsetDateTime.now(),
                "test-bootstrap");
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
