package com.linkplatform.api.owner.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "link-platform.webhooks.allow-private-callback-hosts=true",
        "link-platform.webhooks.allow-http-callbacks=true",
        "link-platform.webhooks.parked-threshold=2",
        "link-platform.webhooks.disable-threshold=3",
        "link-platform.webhooks.connect-timeout-seconds=1",
        "link-platform.webhooks.request-timeout-seconds=1"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class WebhookDeliveryRelayIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void relayParksThenDisablesAfterRepeatedFailures() {
        WorkspaceAccessContext context = createWorkspaceContext("relay-failure-workspace");
        WebhookSubscriptionsService.CreatedSubscription created = webhookSubscriptionsService.createSubscription(
                context,
                "relay-failure",
                "http://127.0.0.1:1/fail",
                Set.of(WebhookEventType.LINK_CREATED),
                true);

        publishAndRelay(context.workspaceId(), "relay-fail-1");
        assertThat(webhookDeliveryStore.findBySubscription(context.workspaceId(), created.record().id(), 10))
                .extracting(WebhookDeliveryRecord::status)
                .contains(WebhookDeliveryStatus.FAILED);
        publishAndRelay(context.workspaceId(), "relay-fail-2");
        assertThat(webhookDeliveryStore.findBySubscription(context.workspaceId(), created.record().id(), 10))
                .extracting(WebhookDeliveryRecord::status)
                .contains(WebhookDeliveryStatus.PARKED);
        publishAndRelay(context.workspaceId(), "relay-fail-3");

        assertThat(webhookDeliveryStore.findBySubscription(context.workspaceId(), created.record().id(), 10))
                .extracting(WebhookDeliveryRecord::status)
                .contains(WebhookDeliveryStatus.DISABLED);
        Integer disabledEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM owner_security_events WHERE workspace_id = ? AND event_type = 'WEBHOOK_SUBSCRIPTION_DISABLED'",
                Integer.class,
                context.workspaceId());
        assertEquals(1, disabledEvents == null ? 0 : disabledEvents);
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
}
