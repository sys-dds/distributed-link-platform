package com.linkplatform.api.owner.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "link-platform.webhooks.parked-threshold=2",
        "link-platform.webhooks.disable-threshold=3",
        "link-platform.webhooks.connect-timeout-seconds=2",
        "link-platform.webhooks.request-timeout-seconds=2"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class WebhookEndToEndIntegrationTest {

    @Autowired
    private WorkspaceStore workspaceStore;

    @Autowired
    private WorkspacePlanStore workspacePlanStore;

    @Autowired
    private WorkspaceRetentionPolicyStore workspaceRetentionPolicyStore;

    @Autowired
    private WebhookSubscriptionsStore webhookSubscriptionsStore;

    @Autowired
    private WebhookDeliveryStore webhookDeliveryStore;

    @Autowired
    private WebhookEventPublisher webhookEventPublisher;

    @Autowired
    private WebhookSubscriptionsService webhookSubscriptionsService;

    @Autowired
    private WebhookDeliveryRelay webhookDeliveryRelay;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private HttpServer httpServer;

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    void publishesDispatchesReplaysAndEnforcesMonthlyQuota() throws Exception {
        AtomicReference<Map<String, String>> lastHeaders = new AtomicReference<>(Map.of());
        AtomicReference<String> lastBody = new AtomicReference<>();
        httpServer = startServer(200, exchange -> {
            lastHeaders.set(readHeaders(exchange));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeResponse(exchange, 200, "ok");
        });

        WorkspaceAccessContext context = createWorkspaceContext("webhook-e2e-success");
        jdbcTemplate.update(
                "UPDATE workspace_plans SET monthly_webhook_deliveries_limit = 1 WHERE workspace_id = ?",
                context.workspaceId());

        WebhookSubscriptionRecord subscription = webhookSubscriptionsStore.create(
                context.workspaceId(),
                "sink",
                "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/hook",
                "test-secret-hash",
                "test-secret",
                true,
                Set.of(WebhookEventType.LINK_CREATED),
                OffsetDateTime.now());

        webhookEventPublisher.publish(
                context.workspaceId(),
                WebhookEventType.LINK_CREATED,
                "evt-1",
                objectMapper.createObjectNode().put("slug", "alpha"));
        WebhookDeliveryRecord firstDelivery = webhookDeliveryStore.findBySubscription(context.workspaceId(), subscription.id(), 10).getFirst();
        assertEquals(WebhookDeliveryStatus.PENDING, firstDelivery.status());

        webhookDeliveryRelay.relayDueDeliveries();

        WebhookDeliveryRecord delivered = webhookDeliveryStore.findById(context.workspaceId(), subscription.id(), firstDelivery.id()).orElseThrow();
        assertEquals(WebhookDeliveryStatus.DELIVERED, delivered.status());
        assertEquals("link.created", lastHeaders.get().get("X-LinkPlatform-Event"));
        assertNotNull(lastHeaders.get().get("X-LinkPlatform-Signature"));
        assertNotNull(lastHeaders.get().get("X-LinkPlatform-Timestamp"));
        assertEquals(String.valueOf(delivered.id()), lastHeaders.get().get("X-LinkPlatform-Delivery-Id"));
        assertEquals(context.workspaceSlug(), lastHeaders.get().get("X-LinkPlatform-Workspace-Slug"));
        assertTrue(lastBody.get().contains("\"slug\":\"alpha\""));

        WebhookDeliveryRecord replay = webhookSubscriptionsService.replayDelivery(context, subscription.id(), delivered.id());
        assertNotEquals(delivered.id(), replay.id());
        assertEquals(WebhookDeliveryStatus.PENDING, replay.status());

        webhookEventPublisher.publish(
                context.workspaceId(),
                WebhookEventType.LINK_CREATED,
                "evt-2",
                objectMapper.createObjectNode().put("slug", "beta"));
        webhookDeliveryRelay.relayDueDeliveries();

        WebhookDeliveryRecord parked = webhookDeliveryStore.findBySubscription(context.workspaceId(), subscription.id(), 20).stream()
                .filter(record -> "evt-2".equals(record.eventId()))
                .findFirst()
                .orElseThrow();
        assertEquals(WebhookDeliveryStatus.PARKED, parked.status());
        Long quotaEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM owner_security_events WHERE workspace_id = ? AND event_type = 'WEBHOOK_DELIVERY_QUOTA_EXCEEDED'",
                Long.class,
                context.workspaceId());
        assertEquals(1L, quotaEvents == null ? 0L : quotaEvents);
    }

    @Test
    void repeatedFailuresParkAndDisableSubscription() throws Exception {
        httpServer = startServer(500, exchange -> writeResponse(exchange, 500, "fail"));
        WorkspaceAccessContext context = createWorkspaceContext("webhook-e2e-failure");

        WebhookSubscriptionRecord subscription = webhookSubscriptionsStore.create(
                context.workspaceId(),
                "sink",
                "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/hook",
                "failure-secret-hash",
                "failure-secret",
                true,
                Set.of(WebhookEventType.LINK_CREATED),
                OffsetDateTime.now());

        publishAndRelay(context.workspaceId(), "fail-1");
        publishAndRelay(context.workspaceId(), "fail-2");
        publishAndRelay(context.workspaceId(), "fail-3");

        WebhookSubscriptionRecord storedSubscription = webhookSubscriptionsStore.findById(context.workspaceId(), subscription.id()).orElseThrow();
        assertNotNull(storedSubscription.disabledAt());
        assertEquals(false, storedSubscription.enabled());

        Map<String, WebhookDeliveryStatus> statusesByEvent = webhookDeliveryStore.findBySubscription(context.workspaceId(), subscription.id(), 20).stream()
                .collect(java.util.stream.Collectors.toMap(WebhookDeliveryRecord::eventId, WebhookDeliveryRecord::status));
        assertEquals(WebhookDeliveryStatus.FAILED, statusesByEvent.get("fail-1"));
        assertEquals(WebhookDeliveryStatus.PARKED, statusesByEvent.get("fail-2"));
        assertEquals(WebhookDeliveryStatus.DISABLED, statusesByEvent.get("fail-3"));
    }

    private void publishAndRelay(long workspaceId, String eventId) {
        webhookEventPublisher.publish(
                workspaceId,
                WebhookEventType.LINK_CREATED,
                eventId,
                objectMapper.createObjectNode().put("slug", eventId));
        webhookDeliveryRelay.relayDueDeliveries();
    }

    private WorkspaceAccessContext createWorkspaceContext(String slug) {
        OffsetDateTime now = OffsetDateTime.now();
        WorkspaceRecord workspace = workspaceStore.createWorkspace(slug, slug, false, now, 1L);
        workspaceStore.addMember(workspace.id(), 1L, WorkspaceRole.OWNER, now, 1L);
        workspacePlanStore.upsertPlan(workspace.id(), WorkspacePlanCode.FREE, now);
        workspaceRetentionPolicyStore.upsert(workspace.id(), 365, 365, 90, 365, 365, now, 1L);
        return new WorkspaceAccessContext(
                new AuthenticatedOwner(1L, "free-owner", "Free Owner", OwnerPlan.FREE),
                workspace.id(),
                workspace.slug(),
                workspace.displayName(),
                false,
                WorkspaceRole.OWNER,
                WorkspaceRole.OWNER.impliedScopes(),
                "test-api-key-hash");
    }

    private HttpServer startServer(int defaultStatus, ExchangeHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/hook", exchange -> handler.handle(exchange));
        server.start();
        return server;
    }

    private Map<String, String> readHeaders(HttpExchange exchange) {
        Map<String, String> headers = new ConcurrentHashMap<>();
        exchange.getRequestHeaders().forEach((key, values) -> headers.put(key, values.getFirst()));
        return headers;
    }

    private void writeResponse(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
