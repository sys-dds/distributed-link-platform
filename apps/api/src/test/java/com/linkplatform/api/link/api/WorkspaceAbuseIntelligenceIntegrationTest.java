package com.linkplatform.api.link.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class WorkspaceAbuseIntelligenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void hostRulesAndWorkspacePolicyThresholdsInfluenceBehavior() throws Exception {
        createWorkspace("team-abuse-intel");
        String opsKey = bootstrapWorkspaceApiKey("team-abuse-intel", "team-abuse-ops", "[\"ops:read\",\"ops:write\"]");
        String linksKey = bootstrapWorkspaceApiKey("team-abuse-intel", "team-abuse-links", "[\"links:read\",\"links:write\"]");

        mockMvc.perform(post("/api/v1/workspaces/current/abuse/host-rules")
                        .header("X-API-Key", opsKey)
                        .header("X-Workspace-Slug", "team-abuse-intel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"host":"xn--bcher-kva.example","ruleType":"ALLOW","note":"allow review bypass"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleType").value("ALLOW"));

        mockMvc.perform(post("/api/v1/links")
                        .header("X-API-Key", linksKey)
                        .header("X-Workspace-Slug", "team-abuse-intel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"allow-punycode","originalUrl":"https://xn--bcher-kva.example/path"}
                                """))
                .andExpect(status().isCreated());

        Integer allowCases = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM link_abuse_cases WHERE workspace_id = (SELECT id FROM workspaces WHERE slug = 'team-abuse-intel') AND slug = 'allow-punycode'",
                Integer.class);
        org.assertj.core.api.Assertions.assertThat(allowCases).isZero();

        mockMvc.perform(post("/api/v1/workspaces/current/abuse/host-rules")
                        .header("X-API-Key", opsKey)
                        .header("X-Workspace-Slug", "team-abuse-intel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"host":"denied.example","ruleType":"DENY","note":"deny target"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/links")
                        .header("X-API-Key", linksKey)
                        .header("X-Workspace-Slug", "team-abuse-intel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"deny-host","originalUrl":"https://denied.example/path"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Blocked target host"));

        mockMvc.perform(patch("/api/v1/workspaces/current/abuse/policy")
                        .header("X-API-Key", opsKey)
                        .header("X-Workspace-Slug", "team-abuse-intel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rawIpReviewEnabled":false,"repeatedHostQuarantineThreshold":1,"redirectRateLimitQuarantineThreshold":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawIpReviewEnabled").value(false))
                .andExpect(jsonPath("$.repeatedHostQuarantineThreshold").value(1));

        mockMvc.perform(post("/api/v1/links")
                        .header("X-API-Key", linksKey)
                        .header("X-Workspace-Slug", "team-abuse-intel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"public-ip-safe","originalUrl":"https://8.8.8.8/safe"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void abuseTrendSummaryReturnsWorkspaceScopedCounts() throws Exception {
        createWorkspace("team-abuse-trends");
        createWorkspace("team-abuse-other");
        String opsKey = bootstrapWorkspaceApiKey("team-abuse-trends", "team-abuse-trends-ops", "[\"ops:read\",\"ops:write\",\"links:read\",\"links:write\"]");
        String otherOpsKey = bootstrapWorkspaceApiKey("team-abuse-other", "team-abuse-other-ops", "[\"ops:read\",\"ops:write\",\"links:read\",\"links:write\"]");

        createLink("team-abuse-trends", opsKey, "trend-a", "https://trend.example/a");
        createLink("team-abuse-trends", opsKey, "trend-b", "https://trend.example/b");
        createLink("team-abuse-other", otherOpsKey, "other-a", "https://other.example/a");

        createManualCase("team-abuse-trends", opsKey, "trend-a", false);
        createManualCase("team-abuse-trends", opsKey, "trend-b", true);
        createManualCase("team-abuse-other", otherOpsKey, "other-a", false);

        mockMvc.perform(get("/api/v1/workspaces/current/abuse/trends")
                        .header("X-API-Key", opsKey)
                        .header("X-Workspace-Slug", "team-abuse-trends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOpenAbuseCases").value(1))
                .andExpect(jsonPath("$.totalQuarantinedLinks").value(1))
                .andExpect(jsonPath("$.topFlaggedHostsLast7d[0].host").value("trend.example"))
                .andExpect(jsonPath("$.latestUpdatedAt").isNotEmpty());
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

    private void createManualCase(String workspaceSlug, String apiKey, String slug, boolean quarantineNow) throws Exception {
        mockMvc.perform(post("/api/v1/ops/abuse/reviews/manual")
                        .header("X-API-Key", apiKey)
                        .header("X-Workspace-Slug", workspaceSlug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"%s","summary":"needs review","quarantineNow":%s}
                                """.formatted(slug, quarantineNow)))
                .andExpect(status().isOk());
    }

    private String bootstrapWorkspaceApiKey(String workspaceSlug, String plaintextKey, String scopesJson) {
        Long workspaceId = jdbcTemplate.queryForObject("SELECT id FROM workspaces WHERE slug = ?", Long.class, workspaceSlug);
        jdbcTemplate.update(
                """
                INSERT INTO owner_api_keys (
                    owner_id, workspace_id, key_prefix, key_hash, key_label, label, scopes_json, created_at, created_by
                ) VALUES (1, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, 'test-bootstrap')
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
