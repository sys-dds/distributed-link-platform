package com.linkplatform.api.owner.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkplatform.api.owner.application.WebhookDeliveryRelay;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "link-platform.webhooks.allow-private-callback-hosts=true",
        "link-platform.webhooks.allow-http-callbacks=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class WebhookPlatformMaturityIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.8")
            .withDatabaseName("link_platform_test")
            .withUsername("link_platform")
            .withPassword("link_platform");

    @Container
    static final GenericContainer<?> WEBHOOK_SINK = new GenericContainer<>("mendhak/http-https-echo:35")
            .withExposedPorts(8080);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WebhookDeliveryRelay webhookDeliveryRelay;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Test
    void verifyTestFireHealthAndEventVersionWorkThroughRealPlatformPaths() throws Exception {
        String apiKey = bootstrapPersonalWorkspaceApiKey("webhook-platform-key");
        String callbackUrl = "http://" + WEBHOOK_SINK.getHost() + ":" + WEBHOOK_SINK.getMappedPort(8080) + "/webhook";

        String created = mockMvc.perform(post("/api/v1/workspaces/current/webhooks")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"platform","callbackUrl":"%s","eventTypes":["link.created"],"enabled":true}
                                """.formatted(callbackUrl)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subscription.eventVersion").value(1))
                .andExpect(jsonPath("$.subscription.verificationStatus").value("UNVERIFIED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long subscriptionId = objectMapper.readTree(created).path("subscription").path("id").asLong();

        mockMvc.perform(post("/api/v1/workspaces/current/webhooks/{subscriptionId}/verify", subscriptionId)
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.verifiedAt").isNotEmpty());

        String testFire = mockMvc.perform(post("/api/v1/workspaces/current/webhooks/{subscriptionId}/test-fire", subscriptionId)
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryId").isNumber())
                .andExpect(jsonPath("$.eventVersion").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long deliveryId = objectMapper.readTree(testFire).path("deliveryId").asLong();

        webhookDeliveryRelay.relayDueDeliveries();

        mockMvc.perform(get("/api/v1/workspaces/current/webhooks/{subscriptionId}/health", subscriptionId)
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.eventVersion").value(1))
                .andExpect(jsonPath("$.lastTestDeliveryId").value(deliveryId))
                .andExpect(jsonPath("$.lastTestFiredAt").isNotEmpty());

        Integer delivered = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM webhook_deliveries WHERE id = ? AND status = 'DELIVERED'",
                Integer.class,
                deliveryId);
        assertThat(delivered).isEqualTo(1);
        String sinkLogs = WEBHOOK_SINK.getLogs();
        assertThat(sinkLogs.toLowerCase()).contains("x-linkplatform-event-version");
    }

    private String bootstrapPersonalWorkspaceApiKey(String plaintextKey) {
        Long workspaceId = jdbcTemplate.queryForObject("SELECT id FROM workspaces WHERE slug = 'free-owner'", Long.class);
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
                "[\"webhooks:read\",\"webhooks:write\",\"links:read\",\"links:write\"]",
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
