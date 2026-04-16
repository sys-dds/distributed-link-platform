package com.linkplatform.api.link.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AnalyticsPipelineLeaseRecoveryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void statusCountsExpiredClaimAsEligibleForLeaseRecovery() throws Exception {
        String apiKey = bootstrapOpsReadKey();
        jdbcTemplate.update(
                """
                INSERT INTO analytics_outbox (
                    event_id, event_type, event_key, payload_json, claimed_by, claimed_until, created_at
                ) VALUES ('lease-status-1', 'redirect-click', 'lease-status-key', '{}', 'dead-worker', ?, ?)
                """,
                OffsetDateTime.now().minusMinutes(2),
                OffsetDateTime.now().minusMinutes(5));

        mockMvc.perform(get("/api/v1/analytics/pipeline")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipelineName").value("analytics"))
                .andExpect(jsonPath("$.eligibleCount").value(1))
                .andExpect(jsonPath("$.parkedCount").value(0))
                .andExpect(jsonPath("$.oldestEligibleAgeSeconds").isNumber())
                .andExpect(jsonPath("$.oldestParkedAgeSeconds").doesNotExist());
    }

    private String bootstrapOpsReadKey() {
        long workspaceId = jdbcTemplate.queryForObject(
                "SELECT id FROM workspaces WHERE created_by_owner_id = 1 AND personal_workspace = TRUE LIMIT 1",
                Long.class);
        String plaintextKey = "analytics-lease-status-key";
        jdbcTemplate.update(
                """
                INSERT INTO owner_api_keys (owner_id, workspace_id, key_prefix, key_hash, key_label, label, scopes_json, created_at, created_by)
                VALUES (1, ?, ?, ?, ?, ?, CAST('[\"ops:read\"]' AS jsonb), ?, 'lease-recovery-test')
                """,
                workspaceId,
                plaintextKey,
                sha256(plaintextKey),
                plaintextKey,
                plaintextKey,
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
