package com.linkplatform.api.link.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
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
        org.assertj.core.api.Assertions.assertThat(allowCases).isEqualTo(1);

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
                .andExpect(status().isCreated());

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

        mockMvc.perform(get("/api/v1/workspaces/current/abuse/host-rules")
                        .header("X-API-Key", opsKey)
                        .header("X-Workspace-Slug", "team-abuse-intel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

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
    void abusePolicyAndHostRulesRemainWorkspaceScopedAndPersistTimestamps() throws Exception {
        createWorkspace("team-abuse-trends");
        createWorkspace("team-abuse-other");
        String opsKey = bootstrapWorkspaceApiKey("team-abuse-trends", "team-abuse-trends-ops", "[\"ops:read\",\"ops:write\"]");
        String otherOpsKey = bootstrapWorkspaceApiKey("team-abuse-other", "team-abuse-other-ops", "[\"ops:read\",\"ops:write\"]");

        mockMvc.perform(patch("/api/v1/workspaces/current/abuse/policy")
                        .header("X-API-Key", opsKey)
                        .header("X-Workspace-Slug", "team-abuse-trends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rawIpReviewEnabled":false,"punycodeReviewEnabled":false,"repeatedHostQuarantineThreshold":2,"redirectRateLimitQuarantineThreshold":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawIpReviewEnabled").value(false))
                .andExpect(jsonPath("$.punycodeReviewEnabled").value(false))
                .andExpect(jsonPath("$.repeatedHostQuarantineThreshold").value(2))
                .andExpect(jsonPath("$.redirectRateLimitQuarantineThreshold").value(3))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedByOwnerId").value(1));

        mockMvc.perform(post("/api/v1/workspaces/current/abuse/host-rules")
                        .header("X-API-Key", opsKey)
                        .header("X-Workspace-Slug", "team-abuse-trends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"host":"trend.example","ruleType":"ALLOW","note":"workspace scoped"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/workspaces/current/abuse/policy")
                        .header("X-API-Key", otherOpsKey)
                        .header("X-Workspace-Slug", "team-abuse-other"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawIpReviewEnabled").value(true))
                .andExpect(jsonPath("$.punycodeReviewEnabled").value(true))
                .andExpect(jsonPath("$.repeatedHostQuarantineThreshold").value(3))
                .andExpect(jsonPath("$.redirectRateLimitQuarantineThreshold").value(5))
                .andExpect(jsonPath("$.updatedAt").doesNotExist());

        mockMvc.perform(get("/api/v1/workspaces/current/abuse/host-rules")
                        .header("X-API-Key", otherOpsKey)
                        .header("X-Workspace-Slug", "team-abuse-other"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        OffsetDateTime updatedAt = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM workspace_abuse_policies WHERE workspace_id = (SELECT id FROM workspaces WHERE slug = 'team-abuse-trends')",
                OffsetDateTime.class);
        OffsetDateTime ruleCreatedAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM workspace_host_rules WHERE workspace_id = (SELECT id FROM workspaces WHERE slug = 'team-abuse-trends') AND host = 'trend.example'",
                OffsetDateTime.class);
        org.assertj.core.api.Assertions.assertThat(updatedAt).isNotNull();
        org.assertj.core.api.Assertions.assertThat(ruleCreatedAt).isNotNull();
        org.assertj.core.api.Assertions.assertThat(updatedAt).isBeforeOrEqualTo(OffsetDateTime.now(Clock.systemUTC()));
        org.assertj.core.api.Assertions.assertThat(ruleCreatedAt).isBeforeOrEqualTo(OffsetDateTime.now(Clock.systemUTC()));
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
