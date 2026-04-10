package com.linkplatform.api.owner.api;

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
class ServiceAccountLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void serviceAccountCreateAndDisableWorks() throws Exception {
        createWorkspace("team-service");
        String adminKey = bootstrapWorkspaceApiKey("team-service", 1L, "team-service-admin", scopes());

        String created = mockMvc.perform(post("/api/v1/workspaces/{workspaceSlug}/service-accounts", "team-service")
                        .header("X-API-Key", adminKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Relay Bot","slug":"relay-bot","role":"editor"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("relay-bot"))
                .andExpect(jsonPath("$.status").value("active"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long serviceAccountId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(created).path("id").asLong();
        String serviceAccountOwnerKey = jdbcTemplate.queryForObject(
                "SELECT owner_key FROM owners WHERE id = ?",
                String.class,
                serviceAccountId);
        org.assertj.core.api.Assertions.assertThat(serviceAccountOwnerKey).isEqualTo("svc-%d-relay-bot".formatted(
                jdbcTemplate.queryForObject(
                        "SELECT id FROM workspaces WHERE slug = 'team-service'",
                        Long.class)));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceSlug}/service-accounts", "team-service")
                        .header("X-API-Key", adminKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("relay-bot"))
                .andExpect(jsonPath("$[0].status").value("active"));

        mockMvc.perform(post("/api/v1/workspaces/{workspaceSlug}/service-accounts/{serviceAccountId}/disable", "team-service", serviceAccountId)
                        .header("X-API-Key", adminKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("disabled"));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceSlug}/service-accounts", "team-service")
                        .header("X-API-Key", adminKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("disabled"));

        Integer disabledMemberships = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workspace_members WHERE workspace_id = (SELECT id FROM workspaces WHERE slug = 'team-service') AND owner_id = ? AND member_type = 'SERVICE_ACCOUNT' AND suspended_at IS NOT NULL",
                Integer.class,
                serviceAccountId);
        org.assertj.core.api.Assertions.assertThat(disabledMemberships).isEqualTo(1);
        OffsetDateTime disabledAt = jdbcTemplate.queryForObject(
                "SELECT disabled_at FROM service_accounts WHERE id = ?",
                OffsetDateTime.class,
                serviceAccountId);
        org.assertj.core.api.Assertions.assertThat(disabledAt).isNotNull();
    }

    @Test
    void suspendedMemberLosesAccessAndSuspendedWorkspaceBlocksMutations() throws Exception {
        createWorkspace("team-suspend");
        ensureOwnerWithPersonalWorkspace(30L, "member30@example.com", "member30-key");
        addMember("team-suspend", 30L, "ADMIN");
        String adminKey = bootstrapWorkspaceApiKey("team-suspend", 1L, "team-suspend-admin", scopes());
        bootstrapWorkspaceApiKey("team-suspend", 30L, "team-suspend-member", scopes());

        mockMvc.perform(post("/api/v1/workspaces/{workspaceSlug}/members/{ownerId}/suspend", "team-suspend", 30L)
                        .header("X-API-Key", adminKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"paused"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/workspaces/current")
                        .header("X-API-Key", "team-suspend-member")
                        .header("X-Workspace-Slug", "team-suspend"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.category").value("workspace-suspended"));

        mockMvc.perform(post("/api/v1/workspaces/{workspaceSlug}/status", "team-suspend")
                        .header("X-API-Key", adminKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SUSPENDED","reason":"ops hold"}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/workspaces/{workspaceSlug}/service-accounts", "team-suspend")
                        .header("X-API-Key", adminKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Blocked Bot","slug":"blocked-bot","role":"viewer"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.category").value("workspace-suspended"));
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

    private void ensureOwnerWithPersonalWorkspace(long ownerId, String ownerKey, String apiKey) {
        Integer ownerCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM owners WHERE id = ?", Integer.class, ownerId);
        if (ownerCount == null || ownerCount == 0) {
            jdbcTemplate.update(
                    "INSERT INTO owners (id, owner_key, display_name, plan, created_at) VALUES (?, ?, ?, 'FREE', ?)",
                    ownerId,
                    ownerKey,
                    ownerKey,
                    OffsetDateTime.now());
        }
        Integer workspaceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workspaces WHERE created_by_owner_id = ? AND personal_workspace = TRUE",
                Integer.class,
                ownerId);
        if (workspaceCount == null || workspaceCount == 0) {
            jdbcTemplate.update(
                    "INSERT INTO workspaces (slug, display_name, personal_workspace, status, created_at, created_by_owner_id) VALUES (?, ?, TRUE, 'ACTIVE', ?, ?)",
                    "owner-" + ownerId,
                    ownerKey,
                    OffsetDateTime.now(),
                    ownerId);
            Long workspaceId = jdbcTemplate.queryForObject(
                    "SELECT id FROM workspaces WHERE created_by_owner_id = ? AND personal_workspace = TRUE",
                    Long.class,
                    ownerId);
            jdbcTemplate.update(
                    "INSERT INTO workspace_members (workspace_id, owner_id, role, member_type, joined_at, added_by_owner_id, removed_at) VALUES (?, ?, 'OWNER', 'HUMAN', ?, ?, NULL)",
                    workspaceId,
                    ownerId,
                    OffsetDateTime.now(),
                    ownerId);
            jdbcTemplate.update(
                    """
                    INSERT INTO workspace_plans (
                        workspace_id, plan_code, subscription_status, active_links_limit, members_limit, api_keys_limit,
                        webhooks_limit, monthly_webhook_deliveries_limit, exports_enabled, current_period_start, current_period_end,
                        created_at, updated_at
                    ) VALUES (?, 'FREE', 'ACTIVE', 100, 5, 10, 5, 10000, TRUE, ?, ?, ?, ?)
                    """,
                    workspaceId,
                    OffsetDateTime.now(),
                    OffsetDateTime.now().plusDays(30),
                    OffsetDateTime.now(),
                    OffsetDateTime.now());
        }
        Integer keyCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM owner_api_keys WHERE key_prefix = ?", Integer.class, apiKey);
        if (keyCount == null || keyCount == 0) {
            Long workspaceId = jdbcTemplate.queryForObject(
                    "SELECT id FROM workspaces WHERE created_by_owner_id = ? AND personal_workspace = TRUE",
                    Long.class,
                    ownerId);
            jdbcTemplate.update(
                    """
                    INSERT INTO owner_api_keys (owner_id, workspace_id, key_prefix, key_hash, key_label, label, scopes_json, created_at, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, 'test-bootstrap')
                    """,
                    ownerId,
                    workspaceId,
                    apiKey,
                    sha256(apiKey),
                    apiKey,
                    apiKey,
                    scopes(),
                    OffsetDateTime.now());
        }
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

    private String scopes() {
        return "[\"members:read\",\"members:write\",\"links:read\",\"links:write\",\"webhooks:read\",\"webhooks:write\",\"ops:read\",\"ops:write\"]";
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
