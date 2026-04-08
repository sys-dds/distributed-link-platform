package com.linkplatform.api.owner.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "link-platform.webhooks.allow-private-callback-hosts=false",
        "link-platform.webhooks.allow-http-callbacks=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class WebhookCallbackValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("dataSource")
    private DataSource dataSource;

    @Test
    void strictDefaultApiPathRejectsHttpCallbacks() throws Exception {
        mockMvc.perform(post("/api/v1/workspaces/current/webhooks")
                        .header("X-API-Key", bootstrapPersonalWorkspaceApiKey("strict-http-key", "[\"webhooks:write\"]"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"strict-http","callbackUrl":"http://hooks.example.com/callback","eventTypes":["link.created"],"enabled":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Webhook callbackUrl must use https"))
                .andExpect(jsonPath("$.category").value("bad-request"));
    }

    @Test
    void strictDefaultApiPathRejectsLocalhostAndPrivateHosts() throws Exception {
        String apiKey = bootstrapPersonalWorkspaceApiKey("strict-private-key", "[\"webhooks:write\"]");

        mockMvc.perform(post("/api/v1/workspaces/current/webhooks")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"strict-localhost","callbackUrl":"https://localhost:8443/callback","eventTypes":["link.created"],"enabled":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Webhook callbackUrl must not target localhost or private addresses"));

        mockMvc.perform(post("/api/v1/workspaces/current/webhooks")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"strict-private","callbackUrl":"https://127.0.0.1:8443/callback","eventTypes":["link.created"],"enabled":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Webhook callbackUrl must not target localhost or private addresses"));
    }

    private String bootstrapPersonalWorkspaceApiKey(String plaintextKey, String scopesJson) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long workspaceId = jdbcTemplate.queryForObject(
                "SELECT id FROM workspaces WHERE personal_workspace = TRUE AND created_by_owner_id = 1",
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
