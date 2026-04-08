package com.linkplatform.api.owner.api;

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
class WorkspaceSubscriptionLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void operatorCanSetLifecycleScheduleDowngradeAndSuspensionBlocksWrites() throws Exception {
        createWorkspace("team-subscription");
        String opsKey = bootstrapWorkspaceApiKey("team-subscription", 1L, "team-subscription-ops", "[\"ops:read\",\"ops:write\"]");
        String memberKey = bootstrapWorkspaceApiKey("team-subscription", 1L, "team-subscription-member", "[\"members:read\",\"members:write\"]");
        String linkKey = bootstrapWorkspaceApiKey("team-subscription", 1L, "team-subscription-links", "[\"links:read\",\"links:write\"]");

        mockMvc.perform(patch("/api/v1/ops/workspaces/{workspaceSlug}/plan", "team-subscription")
                        .header("X-API-Key", opsKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"planCode":"PRO"}
                                """))
                .andExpect(status().isOk());

        for (long ownerId = 40L; ownerId <= 44L; ownerId++) {
            ensureOwner(ownerId, "owner" + ownerId + "@example.com");
            addMember("team-subscription", ownerId, "VIEWER");
        }

        mockMvc.perform(patch("/api/v1/ops/workspaces/{workspaceSlug}/subscription", "team-subscription")
                        .header("X-API-Key", opsKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subscriptionStatus":"GRACE",
                                  "graceUntil":"%s",
                                  "scheduledPlanCode":"FREE",
                                  "scheduledPlanEffectiveAt":"%s"
                                }
                                """.formatted(OffsetDateTime.now().plusDays(7), OffsetDateTime.now().plusDays(14))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionStatus").value("GRACE"))
                .andExpect(jsonPath("$.scheduledPlanCode").value("FREE"));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceSlug}/members", "team-subscription")
                        .header("X-API-Key", memberKey)
                        .header("X-Workspace-Slug", "team-subscription"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));

        ensureOwner(45L, "owner45@example.com");
        mockMvc.perform(post("/api/v1/workspaces/{workspaceSlug}/members", "team-subscription")
                        .header("X-API-Key", memberKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerId":45,"role":"viewer"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.category").value("quota-exceeded"))
                .andExpect(jsonPath("$.quotaMetric").value("MEMBERS"));

        Integer stillSix = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workspace_members WHERE workspace_id = (SELECT id FROM workspaces WHERE slug = 'team-subscription') AND removed_at IS NULL",
                Integer.class);
        org.assertj.core.api.Assertions.assertThat(stillSix).isEqualTo(6);

        mockMvc.perform(patch("/api/v1/ops/workspaces/{workspaceSlug}/subscription", "team-subscription")
                        .header("X-API-Key", opsKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subscriptionStatus":"SUSPENDED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionStatus").value("SUSPENDED"));

        mockMvc.perform(get("/api/v1/workspaces/current/plan")
                        .header("X-API-Key", memberKey)
                        .header("X-Workspace-Slug", "team-subscription"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/links")
                        .header("X-API-Key", linkKey)
                        .header("X-Workspace-Slug", "team-subscription")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"blocked-by-subscription","originalUrl":"https://example.com/blocked"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.category").value("workspace-suspended"));

        mockMvc.perform(patch("/api/v1/ops/workspaces/{workspaceSlug}/subscription", "team-subscription")
                        .header("X-API-Key", opsKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subscriptionStatus":"ACTIVE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionStatus").value("ACTIVE"));
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

    private void addMember(String workspaceSlug, long ownerId, String role) {
        Long workspaceId = jdbcTemplate.queryForObject("SELECT id FROM workspaces WHERE slug = ?", Long.class, workspaceSlug);
        jdbcTemplate.update(
                "INSERT INTO workspace_members (workspace_id, owner_id, role, member_type, joined_at, added_by_owner_id, removed_at) VALUES (?, ?, ?, 'HUMAN', ?, 1, NULL)",
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

    private String bootstrapWorkspaceApiKey(String workspaceSlug, long ownerId, String plaintextKey, String scopesJson) {
        Long workspaceId = jdbcTemplate.queryForObject("SELECT id FROM workspaces WHERE slug = ?", Long.class, workspaceSlug);
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
