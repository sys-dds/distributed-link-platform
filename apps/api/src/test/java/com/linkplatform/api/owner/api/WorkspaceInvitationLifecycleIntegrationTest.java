package com.linkplatform.api.owner.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class WorkspaceInvitationLifecycleIntegrationTest {

    private static final String FREE_API_KEY = "free-owner-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void invitationCreateListAcceptAndRevokeWorksAndTokenIsOneTime() throws Exception {
        ensureOwnerWithPersonalWorkspace(20L, "invitee@example.com", "invitee-key");
        createWorkspace("team-invite");
        String workspaceKey = bootstrapWorkspaceApiKey(
                "team-invite",
                1L,
                "team-invite-admin",
                membersWriteReadScopes());

        String created = mockMvc.perform(post("/api/v1/workspaces/{workspaceSlug}/invitations", "team-invite")
                        .header("X-API-Key", workspaceKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"invitee@example.com","role":"admin"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitation.status").value("pending"))
                .andExpect(jsonPath("$.invitationToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode createdJson = objectMapper.readTree(created);
        long invitationId = createdJson.path("invitation").path("id").asLong();
        String token = createdJson.path("invitationToken").asText();
        String tokenPrefix = jdbcTemplate.queryForObject(
                "SELECT token_prefix FROM workspace_invitations WHERE id = ?",
                String.class,
                invitationId);
        org.assertj.core.api.Assertions.assertThat(tokenPrefix).isEqualTo(token.substring(0, 12));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceSlug}/invitations", "team-invite")
                        .header("X-API-Key", workspaceKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("invitee@example.com"));

        mockMvc.perform(post("/api/v1/workspaces/{workspaceSlug}/invitations/accept", "team-invite")
                        .header("X-API-Key", "invitee-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        mockMvc.perform(post("/api/v1/workspaces/{workspaceSlug}/invitations/accept", "team-invite")
                        .header("X-API-Key", "invitee-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(token)))
                .andExpect(status().isBadRequest());

        ensureOwnerWithPersonalWorkspace(21L, "revokee@example.com", "revokee-key");

        String revoked = mockMvc.perform(post("/api/v1/workspaces/{workspaceSlug}/invitations", "team-invite")
                        .header("X-API-Key", workspaceKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"revokee@example.com","role":"viewer"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long revokedId = objectMapper.readTree(revoked).path("invitation").path("id").asLong();

        mockMvc.perform(delete(
                        "/api/v1/workspaces/{workspaceSlug}/invitations/{invitationId}",
                        "team-invite",
                        revokedId)
                        .header("X-API-Key", workspaceKey))
                .andExpect(status().isNoContent());

        Integer acceptedMembers = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM workspace_members
                WHERE workspace_id = (SELECT id FROM workspaces WHERE slug = 'team-invite')
                  AND owner_id = 20
                  AND removed_at IS NULL
                """,
                Integer.class);
        org.assertj.core.api.Assertions.assertThat(acceptedMembers).isEqualTo(1);
        Integer revokedPending = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workspace_invitations WHERE id = ? AND status = 'REVOKED'",
                Integer.class,
                revokedId);
        org.assertj.core.api.Assertions.assertThat(revokedPending).isEqualTo(1);
        Integer acceptedState = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workspace_invitations WHERE id = ? AND status = 'ACCEPTED'",
                Integer.class,
                invitationId);
        org.assertj.core.api.Assertions.assertThat(acceptedState).isEqualTo(1);
        OffsetDateTime acceptedAt = jdbcTemplate.queryForObject(
                "SELECT accepted_at FROM workspace_invitations WHERE id = ?",
                OffsetDateTime.class,
                invitationId);
        OffsetDateTime revokedAt = jdbcTemplate.queryForObject(
                "SELECT revoked_at FROM workspace_invitations WHERE id = ?",
                OffsetDateTime.class,
                revokedId);
        org.assertj.core.api.Assertions.assertThat(acceptedAt).isNotNull();
        org.assertj.core.api.Assertions.assertThat(revokedAt).isNotNull();
    }

    @Test
    void expiredInvitationCannotBeAcceptedAndOwnershipTransferWorks() throws Exception {
        ensureOwnerWithPersonalWorkspace(22L, "expired@example.com", "expired-key");
        createWorkspace("team-expire");
        String workspaceKey = bootstrapWorkspaceApiKey(
                "team-expire",
                1L,
                "team-expire-admin",
                membersWriteReadScopes());

        String created = mockMvc.perform(post("/api/v1/workspaces/{workspaceSlug}/invitations", "team-expire")
                        .header("X-API-Key", workspaceKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"expired@example.com","role":"admin"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long invitationId = objectMapper.readTree(created).path("invitation").path("id").asLong();
        String token = objectMapper.readTree(created).path("invitationToken").asText();
        jdbcTemplate.update(
                "UPDATE workspace_invitations SET expires_at = ? WHERE id = ?",
                OffsetDateTime.now().minusMinutes(1),
                invitationId);

        mockMvc.perform(post("/api/v1/workspaces/{workspaceSlug}/invitations/accept", "team-expire")
                        .header("X-API-Key", "expired-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(token)))
                .andExpect(status().isBadRequest());

        ensureOwnerWithPersonalWorkspace(23L, "owner2@example.com", "owner2-key");
        addMember("team-expire", 23L, "ADMIN");

        mockMvc.perform(post("/api/v1/workspaces/{workspaceSlug}/ownership/transfer", "team-expire")
                        .header("X-API-Key", workspaceKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromOwnerId":1,"toOwnerId":23}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/workspaces/{workspaceSlug}/members", "team-expire")
                        .header("X-API-Key", workspaceKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.ownerId == 23)].role").value(org.hamcrest.Matchers.contains("owner")))
                .andExpect(jsonPath("$[?(@.ownerId == 1)].role").value(org.hamcrest.Matchers.contains("admin")));

        Integer expiredState = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workspace_invitations WHERE id = ? AND status = 'EXPIRED'",
                Integer.class,
                invitationId);
        org.assertj.core.api.Assertions.assertThat(expiredState).isEqualTo(1);
    }

    @Test
    void lastHumanOwnerCannotBeRemovedSuspendedOrDemoted() throws Exception {
        createWorkspace("team-last-owner");
        String workspaceKey = bootstrapWorkspaceApiKey(
                "team-last-owner",
                1L,
                "team-last-owner-admin",
                membersWriteReadScopes());

        mockMvc.perform(delete("/api/v1/workspaces/{workspaceSlug}/members/{ownerId}", "team-last-owner", 1L)
                        .header("X-API-Key", workspaceKey))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.category").value("invalid-role-change"));

        mockMvc.perform(post("/api/v1/workspaces/{workspaceSlug}/members/{ownerId}/suspend", "team-last-owner", 1L)
                        .header("X-API-Key", workspaceKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"nope"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.category").value("invalid-role-change"));

        mockMvc.perform(patch("/api/v1/workspaces/{workspaceSlug}/members/{ownerId}", "team-last-owner", 1L)
                        .header("X-API-Key", workspaceKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"admin"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.category").value("invalid-role-change"));
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

    private void addMember(String workspaceSlug, long ownerId, String role) {
        Long workspaceId = jdbcTemplate.queryForObject(
                "SELECT id FROM workspaces WHERE slug = ?",
                Long.class,
                workspaceSlug);
        jdbcTemplate.update(
                """
                INSERT INTO workspace_members (
                    workspace_id, owner_id, role, member_type, joined_at, added_by_owner_id, removed_at
                )
                VALUES (?, ?, ?, 'HUMAN', ?, 1, NULL)
                """,
                workspaceId,
                ownerId,
                role,
                OffsetDateTime.now());
    }

    private void ensureOwnerWithPersonalWorkspace(long ownerId, String ownerKey, String apiKey) {
        Integer ownerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM owners WHERE id = ?",
                Integer.class,
                ownerId);
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
                    """
                    INSERT INTO workspaces (
                        slug, display_name, personal_workspace, status, created_at, created_by_owner_id
                    )
                    VALUES (?, ?, TRUE, 'ACTIVE', ?, ?)
                    """,
                    ownerKey.replace("@", "-").replace(".", "-"),
                    ownerKey,
                    OffsetDateTime.now(),
                    ownerId);
            Long workspaceId = jdbcTemplate.queryForObject(
                    "SELECT id FROM workspaces WHERE created_by_owner_id = ? AND personal_workspace = TRUE",
                    Long.class,
                    ownerId);
            jdbcTemplate.update(
                    """
                    INSERT INTO workspace_members (
                        workspace_id, owner_id, role, member_type, joined_at, added_by_owner_id, removed_at
                    )
                    VALUES (?, ?, 'OWNER', 'HUMAN', ?, ?, NULL)
                    """,
                    workspaceId,
                    ownerId,
                    OffsetDateTime.now(),
                    ownerId);
            jdbcTemplate.update(
                    """
                    INSERT INTO workspace_plans (
                        workspace_id, plan_code, subscription_status, active_links_limit, members_limit, api_keys_limit,
                        webhooks_limit, monthly_webhook_deliveries_limit, exports_enabled,
                        current_period_start, current_period_end,
                        created_at, updated_at
                    ) VALUES (?, 'FREE', 'ACTIVE', 100, 5, 10, 5, 10000, TRUE, ?, ?, ?, ?)
                    """,
                    workspaceId,
                    OffsetDateTime.now(),
                    OffsetDateTime.now().plusDays(30),
                    OffsetDateTime.now(),
                    OffsetDateTime.now());
        }
        Integer keyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM owner_api_keys WHERE key_prefix = ?",
                Integer.class,
                apiKey);
        if (keyCount == null || keyCount == 0) {
            Long workspaceId = jdbcTemplate.queryForObject(
                    "SELECT id FROM workspaces WHERE created_by_owner_id = ? AND personal_workspace = TRUE",
                    Long.class,
                    ownerId);
            jdbcTemplate.update(
                    """
                    INSERT INTO owner_api_keys (
                        owner_id, workspace_id, key_prefix, key_hash, key_label, label, scopes_json,
                        created_at, created_by
                    )
                    VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, 'test-bootstrap')
                    """,
                    ownerId,
                    workspaceId,
                    apiKey,
                    sha256(apiKey),
                    apiKey,
                    apiKey,
                    membersWriteReadScopes(),
                    OffsetDateTime.now());
        }
    }

    private String bootstrapWorkspaceApiKey(
            String workspaceSlug,
            long ownerId,
            String plaintextKey,
            String scopesJson) {
        Long workspaceId = jdbcTemplate.queryForObject(
                "SELECT id FROM workspaces WHERE slug = ?",
                Long.class,
                workspaceSlug);
        jdbcTemplate.update(
                """
                INSERT INTO owner_api_keys (
                    owner_id, workspace_id, key_prefix, key_hash, key_label, label, scopes_json,
                    created_at, created_by
                )
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

    private String membersWriteReadScopes() {
        return """
                ["members:read","members:write","links:read","links:write","webhooks:read","webhooks:write",\
                "ops:read","ops:write","exports:read","exports:write"]
                """;
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
