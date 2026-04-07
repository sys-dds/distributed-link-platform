package com.linkplatform.api.owner.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class WorkspacePlanControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void planAndUsageReadsRequireMembersRead() throws Exception {
        String membersReadKey = bootstrapPersonalWorkspaceApiKey("members-read-key", "[\"members:read\"]");
        String retentionReadKey = bootstrapPersonalWorkspaceApiKey("retention-read-key", "[\"retention:read\"]");

        mockMvc.perform(get("/api/v1/workspaces/current/plan").header("X-API-Key", membersReadKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceSlug").value("free-owner"));
        mockMvc.perform(get("/api/v1/workspaces/current/usage").header("X-API-Key", membersReadKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceSlug").value("free-owner"));

        mockMvc.perform(get("/api/v1/workspaces/current/plan").header("X-API-Key", retentionReadKey))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/workspaces/current/usage").header("X-API-Key", retentionReadKey))
                .andExpect(status().isForbidden());
    }

    @Test
    void retentionReadAndWriteUseExplicitScopes() throws Exception {
        String retentionReadKey = bootstrapPersonalWorkspaceApiKey("retention-read-only", "[\"retention:read\"]");
        String retentionWriteKey = bootstrapPersonalWorkspaceApiKey("retention-write-key", "[\"retention:write\"]");

        mockMvc.perform(get("/api/v1/workspaces/current/retention").header("X-API-Key", retentionReadKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickHistoryDays").isNumber());

        mockMvc.perform(patch("/api/v1/workspaces/current/retention")
                        .header("X-API-Key", retentionReadKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clickHistoryDays":30,"securityEventsDays":30,"webhookDeliveriesDays":30,"abuseCasesDays":30,"operatorActionLogDays":30}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/workspaces/current/retention")
                        .header("X-API-Key", retentionWriteKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clickHistoryDays":30,"securityEventsDays":30,"webhookDeliveriesDays":30,"abuseCasesDays":30,"operatorActionLogDays":30}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickHistoryDays").value(30));
    }

    @Test
    void opsPlanUpdateStillRequiresOpsWrite() throws Exception {
        String membersReadKey = bootstrapPersonalWorkspaceApiKey("members-only-plan", "[\"members:read\"]");
        String opsWriteKey = bootstrapPersonalWorkspaceApiKey("ops-write-plan", "[\"ops:write\"]");

        mockMvc.perform(patch("/api/v1/ops/workspaces/{workspaceSlug}/plan", "free-owner")
                        .header("X-API-Key", membersReadKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"planCode":"PRO"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/ops/workspaces/{workspaceSlug}/plan", "free-owner")
                        .header("X-API-Key", opsWriteKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"planCode":"PRO"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode").value("PRO"));
    }

    private String bootstrapPersonalWorkspaceApiKey(String plaintextKey, String scopesJson) {
        Long workspaceId = jdbcTemplate.queryForObject(
                "SELECT id FROM workspaces WHERE slug = 'free-owner'",
                Long.class);
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
