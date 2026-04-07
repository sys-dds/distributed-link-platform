package com.linkplatform.api.link.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class QuarantinedRedirectIntegrationTest {

    private static final String FREE_API_KEY = "free-owner-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flaggedLinksStillRedirectQuarantinedLinksDoNotAndReleaseRestoresRedirect() throws Exception {
        createWorkspace("redirect-team");
        String opsKey = bootstrapWorkspaceApiKey(
                "redirect-team",
                "redirect-team-ops",
                "[\"links:read\",\"links:write\",\"ops:read\",\"ops:write\"]");

        createLink("redirect-team", opsKey, "flagged-ip", "https://8.8.8.8/path");

        mockMvc.perform(get("/flagged-ip"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(header().string("Location", "https://8.8.8.8/path"));

        mockMvc.perform(get("/api/v1/links/flagged-ip")
                        .header("X-API-Key", opsKey)
                        .header("X-Workspace-Slug", "redirect-team"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abuseStatus").value("flagged"));

        long quarantineCaseId = createManualCase("redirect-team", opsKey, "flagged-ip", true);

        mockMvc.perform(get("/flagged-ip"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/ops/abuse/reviews/manual")
                        .header("X-API-Key", opsKey)
                        .header("X-Workspace-Slug", "redirect-team")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"flagged-ip","summary":"secondary note"}
                                """))
                .andExpect(status().isOk());

        Long dismissCaseId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM link_abuse_cases WHERE workspace_id = (SELECT id FROM workspaces WHERE slug = 'redirect-team') AND status = 'OPEN'",
                Long.class);

        mockMvc.perform(post("/api/v1/ops/abuse/reviews/{caseId}/dismiss", dismissCaseId)
                        .header("X-API-Key", opsKey)
                        .header("X-Workspace-Slug", "redirect-team")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resolutionNote":"dismiss only"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/flagged-ip"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/ops/abuse/reviews/{caseId}/release", quarantineCaseId)
                        .header("X-API-Key", opsKey)
                        .header("X-Workspace-Slug", "redirect-team")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resolutionNote":"safe again"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/flagged-ip"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(header().string("Location", "https://8.8.8.8/path"));
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
                                {"slug":"%s","summary":"manual review","quarantineNow":%s}
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
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
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
