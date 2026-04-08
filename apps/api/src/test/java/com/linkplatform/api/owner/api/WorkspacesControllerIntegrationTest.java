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
class WorkspacesControllerIntegrationTest {

    private static final String FREE_API_KEY = "free-owner-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void currentWorkspaceDefaultsToPersonalWorkspace() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces/current").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("free-owner"))
                .andExpect(jsonPath("$.personalWorkspace").value(true))
                .andExpect(jsonPath("$.role").value("owner"));
    }

    @Test
    void ownerCanCreateWorkspaceAddMemberAndLastOwnerDemotionIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/workspaces")
                        .header("X-API-Key", FREE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"team-alpha","displayName":"Team Alpha"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("team-alpha"))
                .andExpect(jsonPath("$.personalWorkspace").value(false))
                .andExpect(jsonPath("$.role").value("owner"));

        String teamApiKey = bootstrapWorkspaceApiKey("team-alpha", "team-alpha-bootstrap", "team-alpha-key");

        mockMvc.perform(post("/api/v1/workspaces/{workspaceSlug}/members", "team-alpha")
                        .header("X-API-Key", teamApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerId":2,"role":"viewer"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerId").value(2))
                .andExpect(jsonPath("$.role").value("viewer"));

        mockMvc.perform(patch("/api/v1/workspaces/{workspaceSlug}/members/{ownerId}", "team-alpha", 1)
                        .header("X-API-Key", teamApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"admin"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.category").value("invalid-role-change"));
    }

    @Test
    void workspaceLifecycleFieldsDoNotBreakCurrentWorkspaceHappyPath() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces/current").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("free-owner"))
                .andExpect(jsonPath("$.grantedScopes").isArray());
    }

    private String bootstrapWorkspaceApiKey(String workspaceSlug, String plaintextKey, String keyPrefix) {
        Long workspaceId = jdbcTemplate.queryForObject(
                "SELECT id FROM workspaces WHERE slug = ?",
                Long.class,
                workspaceSlug);
        jdbcTemplate.update(
                """
                INSERT INTO owner_api_keys (
                    owner_id, workspace_id, key_prefix, key_hash, key_label, label, scopes_json, created_at, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
                """,
                1L,
                workspaceId,
                keyPrefix,
                sha256(plaintextKey),
                keyPrefix,
                keyPrefix,
                "[\"links:read\",\"links:write\",\"analytics:read\",\"api_keys:read\",\"api_keys:write\",\"members:read\",\"members:write\",\"ops:read\",\"ops:write\"]",
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
