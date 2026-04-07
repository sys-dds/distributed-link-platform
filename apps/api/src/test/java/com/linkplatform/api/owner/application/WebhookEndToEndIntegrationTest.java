package com.linkplatform.api.owner.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "link-platform.webhooks.parked-threshold=2",
        "link-platform.webhooks.disable-threshold=3",
        "link-platform.webhooks.connect-timeout-seconds=2",
        "link-platform.webhooks.request-timeout-seconds=2"
})
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class WebhookEndToEndIntegrationTest {

    @Container
    static final GenericContainer<?> WEBHOOK_SINK = new GenericContainer<>("mendhak/http-https-echo:35")
            .withExposedPorts(8080);

    @Autowired
    private WorkspaceStore workspaceStore;

    @Autowired
    private WorkspacePlanStore workspacePlanStore;

    @Autowired
    private WorkspaceRetentionPolicyStore workspaceRetentionPolicyStore;

    @Autowired
    private WebhookSubscriptionsService webhookSubscriptionsService;

    @Autowired
    private WebhookEventPublisher webhookEventPublisher;

    @Autowired
    private WebhookDeliveryRelay webhookDeliveryRelay;

    @Autowired
    private WebhookDeliveryStore webhookDeliveryStore;

    @Autowired
    private WorkspaceEntitlementService workspaceEntitlementService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void serviceLevelWebhookFlowUsesContainerSinkReplayAndQuotaOnActualAttempts() {
        WorkspaceAccessContext context = createWorkspaceContext("webhook-e2e-service");
        WorkspaceAccessContext failingContext = createWorkspaceContext("webhook-e2e-service-failure");

        WebhookSubscriptionsService.CreatedSubscription created = webhookSubscriptionsService.createSubscription(
                context,
                "sink",
                sinkUrl(),
                Set.of(WebhookEventType.LINK_CREATED),
                true);
        assertThat(created.plaintextSecret()).startsWith("whs_");

        webhookEventPublisher.publish(
                context.workspaceId(),
                WebhookEventType.LINK_CREATED,
                "service-event-1",
                objectMapper.createObjectNode().put("slug", "alpha"));
        webhookDeliveryRelay.relayDueDeliveries();

        WebhookDeliveryRecord delivered = webhookDeliveryStore.findBySubscription(context.workspaceId(), created.record().id(), 10).getFirst();
        assertEquals(WebhookDeliveryStatus.DELIVERED, delivered.status());
        assertEquals(1L, workspaceEntitlementService.currentUsage(context.workspaceId()).currentMonthWebhookDeliveries());
        assertThat(WEBHOOK_SINK.getLogs().toLowerCase())
                .contains("x-linkplatform-signature")
                .contains("x-linkplatform-event")
                .contains(context.workspaceSlug().toLowerCase());

        WebhookDeliveryRecord replay = webhookSubscriptionsService.replayDelivery(context, created.record().id(), delivered.id());
        assertNotEquals(delivered.id(), replay.id());
        webhookDeliveryRelay.relayDueDeliveries();

        assertThat(webhookDeliveryStore.findBySubscription(context.workspaceId(), created.record().id(), 10))
                .extracting(WebhookDeliveryRecord::status)
                .contains(WebhookDeliveryStatus.DELIVERED);

        WebhookSubscriptionsService.CreatedSubscription failing = webhookSubscriptionsService.createSubscription(
                failingContext,
                "failing",
                "http://127.0.0.1:1/fail",
                Set.of(WebhookEventType.LINK_CREATED),
                true);

        publishAndRelay(failingContext.workspaceId(), "fail-1");
        assertThat(webhookDeliveryStore.findBySubscription(failingContext.workspaceId(), failing.record().id(), 10))
                .extracting(WebhookDeliveryRecord::status)
                .contains(WebhookDeliveryStatus.FAILED);
        publishAndRelay(failingContext.workspaceId(), "fail-2");
        assertThat(webhookDeliveryStore.findBySubscription(failingContext.workspaceId(), failing.record().id(), 10))
                .extracting(WebhookDeliveryRecord::status)
                .contains(WebhookDeliveryStatus.PARKED);
        publishAndRelay(failingContext.workspaceId(), "fail-3");

        List<WebhookDeliveryRecord> failureRows = webhookDeliveryStore.findBySubscription(failingContext.workspaceId(), failing.record().id(), 10);
        assertThat(failureRows).extracting(WebhookDeliveryRecord::status)
                .contains(WebhookDeliveryStatus.DISABLED);

        assertThat(workspaceEntitlementService.currentUsage(failingContext.workspaceId()).currentMonthWebhookDeliveries())
                .isGreaterThanOrEqualTo(3L);
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

    private String sinkUrl() {
        return "http://" + WEBHOOK_SINK.getHost() + ":" + WEBHOOK_SINK.getMappedPort(8080) + "/";
    }
}
