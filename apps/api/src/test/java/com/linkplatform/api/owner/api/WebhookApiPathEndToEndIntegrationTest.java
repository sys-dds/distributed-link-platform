package com.linkplatform.api.owner.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import java.time.Duration;
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
        "link-platform.webhooks.allow-http-callbacks=true",
        "link-platform.webhooks.parked-threshold=2",
        "link-platform.webhooks.disable-threshold=3",
        "link-platform.webhooks.connect-timeout-seconds=2",
        "link-platform.webhooks.request-timeout-seconds=2"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class WebhookApiPathEndToEndIntegrationTest {

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
    void webhookFlowUsesRealApiPathReplayFailureAndQuotaAgainstContainerSink() throws Exception {
        String scopedApiKey = bootstrapPersonalWorkspaceApiKey(
                "webhook-api-path-key",
                "[\"links:write\",\"webhooks:read\",\"webhooks:write\"]");
        String callbackUrl = sinkUrl();
        assertThat(callbackUrl).startsWith("http://");
        assertThat(callbackUrl).contains(WEBHOOK_SINK.getHost());

        mockMvc.perform(post("/api/v1/workspaces/current/webhooks")
                        .header("X-API-Key", scopedApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"api-path-strict-check","callbackUrl":"https://127.0.0.1:8443/blocked","eventTypes":["link.created"],"enabled":true}
                                """))
                .andExpect(status().isBadRequest());

        String createResponse = mockMvc.perform(post("/api/v1/workspaces/current/webhooks")
                        .header("X-API-Key", scopedApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"api-path","callbackUrl":"%s","eventTypes":["link.created"],"enabled":true}
                                """.formatted(callbackUrl)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.secret").isNotEmpty())
                .andExpect(jsonPath("$.subscription.callbackUrl").value(callbackUrl))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long subscriptionId = objectMapper.readTree(createResponse).path("subscription").path("id").asLong();
        Long workspaceId = jdbcTemplate.queryForObject("SELECT id FROM workspaces WHERE slug = 'free-owner'", Long.class);
        Long storedWorkspaceId = jdbcTemplate.queryForObject(
                "SELECT workspace_id FROM webhook_subscriptions WHERE id = ?",
                Long.class,
                subscriptionId);
        assertEquals(workspaceId, storedWorkspaceId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT callback_url FROM webhook_subscriptions WHERE id = ?",
                String.class,
                subscriptionId)).isEqualTo(callbackUrl);

        String slug = "api-path-" + System.nanoTime();
        createLink(slug);
        String eventId = "link-created:" + slug + ":1";
        long deliveryId = awaitDeliveryId(subscriptionId, eventId);

        webhookDeliveryRelay.relayDueDeliveries();
        awaitDeliveryStatus(deliveryId, "DELIVERED");

        String deliveriesJson = mockMvc.perform(get("/api/v1/workspaces/current/webhooks/{subscriptionId}/deliveries", subscriptionId)
                        .header("X-API-Key", scopedApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("DELIVERED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode deliveries = objectMapper.readTree(deliveriesJson).path("items");
        assertThat(deliveries).isNotEmpty();
        String sinkLogs = awaitSinkLogs();
        assertThat(sinkLogs.toLowerCase())
                .contains("x-linkplatform-signature")
                .contains("x-linkplatform-timestamp")
                .contains("x-linkplatform-event")
                .contains("x-linkplatform-delivery-id")
                .contains("x-linkplatform-workspace-slug");

        String replayResponse = mockMvc.perform(post("/api/v1/workspaces/current/webhooks/{subscriptionId}/deliveries/{deliveryId}/replay", subscriptionId, deliveryId)
                        .header("X-API-Key", scopedApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long replayId = objectMapper.readTree(replayResponse).path("id").asLong();
        assertNotEquals(deliveryId, replayId);
        webhookDeliveryRelay.relayDueDeliveries();
        awaitDeliveryStatus(replayId, "DELIVERED");

        String failingResponse = mockMvc.perform(post("/api/v1/workspaces/current/webhooks")
                        .header("X-API-Key", scopedApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"failing","callbackUrl":"http://127.0.0.1:1/fail","eventTypes":["link.created"],"enabled":true}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long failingSubscriptionId = objectMapper.readTree(failingResponse).path("subscription").path("id").asLong();

        createLink("api-fail-1-" + System.nanoTime());
        webhookDeliveryRelay.relayDueDeliveries();
        assertThat(jdbcTemplate.queryForList(
                "SELECT status FROM webhook_deliveries WHERE subscription_id = ? ORDER BY id",
                String.class,
                failingSubscriptionId)).contains("FAILED");
        createLink("api-fail-2-" + System.nanoTime());
        webhookDeliveryRelay.relayDueDeliveries();
        assertThat(jdbcTemplate.queryForList(
                "SELECT status FROM webhook_deliveries WHERE subscription_id = ? ORDER BY id",
                String.class,
                failingSubscriptionId)).contains("PARKED");
        createLink("api-fail-3-" + System.nanoTime());
        webhookDeliveryRelay.relayDueDeliveries();

        assertThat(jdbcTemplate.queryForList(
                "SELECT status FROM webhook_deliveries WHERE subscription_id = ? ORDER BY id",
                String.class,
                failingSubscriptionId))
                .contains("DISABLED");
        Integer disabledCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM webhook_subscriptions WHERE id = ? AND disabled_at IS NOT NULL AND enabled = FALSE",
                Integer.class,
                failingSubscriptionId);
        assertEquals(1, disabledCount == null ? 0 : disabledCount);

        Long currentUsage = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(quantity), 0)
                FROM workspace_usage_ledger
                WHERE workspace_id = ?
                  AND metric_code = 'WEBHOOK_DELIVERIES'
                """,
                Long.class,
                workspaceId);
        jdbcTemplate.update(
                "UPDATE workspace_plans SET monthly_webhook_deliveries_limit = ? WHERE workspace_id = ?",
                currentUsage,
                workspaceId);

        String quotaSlug = "api-quota-" + System.nanoTime();
        createLink(quotaSlug);
        String quotaEventId = "link-created:" + quotaSlug + ":1";
        long quotaDeliveryId = awaitDeliveryId(subscriptionId, quotaEventId);
        webhookDeliveryRelay.relayDueDeliveries();
        awaitDeliveryStatus(quotaDeliveryId, "PARKED");
        Integer quotaEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM owner_security_events WHERE workspace_id = ? AND event_type = 'WEBHOOK_DELIVERY_QUOTA_EXCEEDED'",
                Integer.class,
                workspaceId);
        assertThat(quotaEvents == null ? 0 : quotaEvents).isGreaterThanOrEqualTo(1);
    }

    private void createLink(String slug) throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .header("X-API-Key", "webhook-api-path-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"%s","originalUrl":"https://example.com/%s"}
                                """.formatted(slug, slug)))
                .andExpect(status().isCreated());
    }

    private long awaitDeliveryId(long subscriptionId, String eventId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            Long id = jdbcTemplate.query(
                            "SELECT id FROM webhook_deliveries WHERE subscription_id = ? AND event_id = ? ORDER BY id DESC",
                            (resultSet, rowNum) -> resultSet.getLong("id"),
                            subscriptionId,
                            eventId)
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (id != null) {
                return id;
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("Timed out waiting for delivery " + eventId);
    }

    private void awaitDeliveryStatus(long deliveryId, String expectedStatus) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            String status = jdbcTemplate.query(
                            "SELECT status FROM webhook_deliveries WHERE id = ?",
                            (resultSet, rowNum) -> resultSet.getString("status"),
                            deliveryId)
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (expectedStatus.equals(status)) {
                return;
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("Timed out waiting for delivery status " + expectedStatus);
    }

    private String awaitSinkLogs() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            String logs = WEBHOOK_SINK.getLogs();
            if (logs != null && !logs.isBlank()) {
                return logs;
            }
            Thread.sleep(100L);
        }
        return WEBHOOK_SINK.getLogs();
    }

    private String sinkUrl() {
        return "http://" + WEBHOOK_SINK.getHost() + ":" + WEBHOOK_SINK.getMappedPort(8080) + "/";
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
