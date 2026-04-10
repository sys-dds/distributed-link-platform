package com.linkplatform.api.owner.application;

import com.linkplatform.api.runtime.ConditionalOnRuntimeModes;
import com.linkplatform.api.runtime.LinkPlatformRuntimeProperties;
import com.linkplatform.api.runtime.RuntimeMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnRuntimeModes({RuntimeMode.ALL, RuntimeMode.WORKER})
public class WebhookDeliveryRelay {

    private final WebhookDeliveryStore webhookDeliveryStore;
    private final WebhookSubscriptionsStore webhookSubscriptionsStore;
    private final WorkspaceEntitlementService workspaceEntitlementService;
    private final SecurityEventStore securityEventStore;
    private final WebhookDispatcher webhookDispatcher;
    private final LinkPlatformRuntimeProperties runtimeProperties;
    private final Clock clock;
    private final String workerId;

    public WebhookDeliveryRelay(
            WebhookDeliveryStore webhookDeliveryStore,
            WebhookSubscriptionsStore webhookSubscriptionsStore,
            WorkspaceEntitlementService workspaceEntitlementService,
            SecurityEventStore securityEventStore,
            WebhookDispatcher webhookDispatcher,
            LinkPlatformRuntimeProperties runtimeProperties,
            Clock clock) {
        this.webhookDeliveryStore = webhookDeliveryStore;
        this.webhookSubscriptionsStore = webhookSubscriptionsStore;
        this.workspaceEntitlementService = workspaceEntitlementService;
        this.securityEventStore = securityEventStore;
        this.webhookDispatcher = webhookDispatcher;
        this.runtimeProperties = runtimeProperties;
        this.clock = clock;
        this.workerId = UUID.randomUUID().toString();
    }

    @Scheduled(fixedDelayString = "${link-platform.webhooks.runner-delay-ms:5000}")
    public void relayDueDeliveries() {
        if (!runtimeProperties.getWebhooks().isEnabled()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<WebhookDeliveryStore.DispatchItem> items = webhookDeliveryStore.claimDueDeliveries(
                workerId,
                now,
                now.plusSeconds(30),
                runtimeProperties.getWebhooks().getDeliveryBatchSize());
        for (WebhookDeliveryStore.DispatchItem item : items) {
            tryDispatch(item, now);
        }
    }

    private void tryDispatch(WebhookDeliveryStore.DispatchItem item, OffsetDateTime now) {
        try {
            workspaceEntitlementService.enforceMonthlyWebhookDeliveryQuota(item.delivery().workspaceId(), 1L);
        } catch (WorkspaceQuotaExceededException exception) {
            webhookDeliveryStore.parkDueDeliveriesForWorkspace(item.delivery().workspaceId(), "Webhook delivery monthly quota exceeded", now);
            securityEventStore.record(
                    SecurityEventType.WEBHOOK_DELIVERY_QUOTA_EXCEEDED,
                    null,
                    item.delivery().workspaceId(),
                    null,
                    "POST",
                    item.subscription().callbackUrl(),
                    null,
                    "Webhook delivery monthly quota exceeded",
                    now);
            return;
        }
        workspaceEntitlementService.recordWebhookDeliveryUsage(
                item.delivery().workspaceId(),
                1L,
                "webhook_delivery_attempt",
                Long.toString(item.delivery().id()),
                now);
        WebhookDispatcher.DispatchResult result = webhookDispatcher.dispatch(item);
        if (result.httpStatus() != null && result.httpStatus() >= 200 && result.httpStatus() < 300) {
            webhookDeliveryStore.markDelivered(item.delivery().id(), now, result.httpStatus(), result.responseBody());
            webhookSubscriptionsStore.recordDeliverySuccess(item.delivery().workspaceId(), item.delivery().subscriptionId(), now);
            return;
        }
        int failureCount = webhookSubscriptionsStore.incrementFailureCount(item.delivery().workspaceId(), item.delivery().subscriptionId(), now);
        int attemptCount = item.delivery().attemptCount() + 1;
        String reason = result.transportError() != null ? result.transportError() : "HTTP " + result.httpStatus();
        if (failureCount >= runtimeProperties.getWebhooks().getDisableThreshold()) {
            webhookSubscriptionsStore.disable(item.delivery().workspaceId(), item.delivery().subscriptionId(), "Webhook disabled after repeated failures", now);
            webhookDeliveryStore.markDisabled(item.delivery().id(), "Webhook disabled after repeated failures", now);
            webhookDeliveryStore.disableQueuedDeliveriesForSubscription(
                    item.delivery().workspaceId(),
                    item.delivery().subscriptionId(),
                    "Webhook disabled after repeated failures",
                    now);
            securityEventStore.record(
                    SecurityEventType.WEBHOOK_SUBSCRIPTION_DISABLED,
                    null,
                    item.delivery().workspaceId(),
                    null,
                    "POST",
                    item.subscription().callbackUrl(),
                    null,
                    "Webhook disabled after repeated failures",
                    now);
            return;
        }
        if (failureCount >= runtimeProperties.getWebhooks().getParkedThreshold()) {
            webhookDeliveryStore.markParked(item.delivery().id(), attemptCount, reason, result.httpStatus(), result.responseBody(), now);
            return;
        }
        webhookDeliveryStore.markFailed(
                item.delivery().id(),
                attemptCount,
                now.plusSeconds((long) Math.min(3600, Math.pow(2, Math.max(0, attemptCount - 1)) * 30L)),
                result.httpStatus(),
                reason,
                result.responseBody());
    }
}
