package com.linkplatform.api.owner.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.linkplatform.api.runtime.LinkPlatformRuntimeProperties;
import java.net.InetAddress;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookSubscriptionsService {

    private static final int DEFAULT_LIMIT = 50;

    private final WebhookSubscriptionsStore webhookSubscriptionsStore;
    private final WebhookDeliveryStore webhookDeliveryStore;
    private final WorkspaceEntitlementService workspaceEntitlementService;
    private final WorkspacePermissionService workspacePermissionService;
    private final SecurityEventStore securityEventStore;
    private final OperatorActionLogStore operatorActionLogStore;
    private final WebhookSigningService webhookSigningService;
    private final LinkPlatformRuntimeProperties runtimeProperties;
    private final Clock clock;

    public WebhookSubscriptionsService(
            WebhookSubscriptionsStore webhookSubscriptionsStore,
            WebhookDeliveryStore webhookDeliveryStore,
            WorkspaceEntitlementService workspaceEntitlementService,
            WorkspacePermissionService workspacePermissionService,
            SecurityEventStore securityEventStore,
            OperatorActionLogStore operatorActionLogStore,
            WebhookSigningService webhookSigningService,
            LinkPlatformRuntimeProperties runtimeProperties) {
        this.webhookSubscriptionsStore = webhookSubscriptionsStore;
        this.webhookDeliveryStore = webhookDeliveryStore;
        this.workspaceEntitlementService = workspaceEntitlementService;
        this.workspacePermissionService = workspacePermissionService;
        this.securityEventStore = securityEventStore;
        this.operatorActionLogStore = operatorActionLogStore;
        this.webhookSigningService = webhookSigningService;
        this.runtimeProperties = runtimeProperties;
        this.clock = Clock.systemUTC();
    }

    @Transactional(readOnly = true)
    public List<WebhookSubscriptionRecord> listSubscriptions(WorkspaceAccessContext context) {
        workspacePermissionService.requireWebhooksRead(context);
        return webhookSubscriptionsStore.findByWorkspaceId(context.workspaceId());
    }

    @Transactional
    public CreatedSubscription createSubscription(
            WorkspaceAccessContext context,
            String name,
            String callbackUrl,
            Set<WebhookEventType> eventTypes,
            boolean enabled) {
        workspacePermissionService.requireWebhooksWrite(context);
        validateCallbackUrl(callbackUrl);
        if (eventTypes == null || eventTypes.isEmpty()) {
            throw new IllegalArgumentException("eventTypes is required");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (enabled) {
            workspaceEntitlementService.enforceWebhooksQuota(context.workspaceId(), enabledWebhookCount(context.workspaceId()));
        }
        WebhookSigningService.GeneratedSecret secret = webhookSigningService.generateSecret();
        WebhookSubscriptionRecord record = webhookSubscriptionsStore.create(
                context.workspaceId(),
                name,
                callbackUrl,
                secret.hash(),
                secret.prefix(),
                enabled,
                eventTypes,
                now);
        workspaceEntitlementService.recordWebhooksSnapshot(
                context.workspaceId(),
                enabledWebhookCount(context.workspaceId()),
                "webhook_create",
                Long.toString(record.id()),
                now);
        securityEventStore.record(
                SecurityEventType.WEBHOOK_SUBSCRIPTION_CREATED,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                "POST",
                "/api/v1/workspaces/current/webhooks",
                null,
                "Webhook subscription created",
                now);
        operatorActionLogStore.record(
                context.workspaceId(),
                context.ownerId(),
                "PIPELINE",
                "webhook_subscription_create",
                null,
                null,
                null,
                "Webhook subscription created",
                now);
        return new CreatedSubscription(record, secret.plaintext());
    }

    @Transactional
    public WebhookSubscriptionRecord updateSubscription(
            WorkspaceAccessContext context,
            long subscriptionId,
            String name,
            Boolean enabled,
            Set<WebhookEventType> eventTypes) {
        workspacePermissionService.requireWebhooksWrite(context);
        WebhookSubscriptionRecord existing = requireSubscription(context.workspaceId(), subscriptionId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (Boolean.TRUE.equals(enabled) && (!existing.enabled() || existing.disabledAt() != null)) {
            workspaceEntitlementService.enforceWebhooksQuota(context.workspaceId(), enabledWebhookCount(context.workspaceId()));
        }
        WebhookSubscriptionRecord updated = webhookSubscriptionsStore.update(
                context.workspaceId(),
                subscriptionId,
                name,
                enabled,
                eventTypes,
                now);
        workspaceEntitlementService.recordWebhooksSnapshot(
                context.workspaceId(),
                enabledWebhookCount(context.workspaceId()),
                "webhook_update",
                Long.toString(updated.id()),
                now);
        securityEventStore.record(
                SecurityEventType.WEBHOOK_SUBSCRIPTION_UPDATED,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                "PATCH",
                "/api/v1/workspaces/current/webhooks/" + subscriptionId,
                null,
                "Webhook subscription updated",
                now);
        operatorActionLogStore.record(
                context.workspaceId(),
                context.ownerId(),
                "PIPELINE",
                "webhook_subscription_update",
                null,
                null,
                null,
                "Webhook subscription updated",
                now);
        return updated;
    }

    @Transactional
    public CreatedSubscription rotateSecret(WorkspaceAccessContext context, long subscriptionId) {
        workspacePermissionService.requireWebhooksWrite(context);
        requireSubscription(context.workspaceId(), subscriptionId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        WebhookSigningService.GeneratedSecret secret = webhookSigningService.generateSecret();
        WebhookSubscriptionRecord updated = webhookSubscriptionsStore.rotateSecret(
                context.workspaceId(),
                subscriptionId,
                secret.hash(),
                secret.prefix(),
                now);
        securityEventStore.record(
                SecurityEventType.WEBHOOK_SECRET_ROTATED,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                "POST",
                "/api/v1/workspaces/current/webhooks/" + subscriptionId + "/rotate-secret",
                null,
                "Webhook secret rotated",
                now);
        operatorActionLogStore.record(
                context.workspaceId(),
                context.ownerId(),
                "PIPELINE",
                "webhook_secret_rotate",
                null,
                null,
                null,
                "Webhook secret rotated",
                now);
        return new CreatedSubscription(updated, secret.plaintext());
    }

    @Transactional(readOnly = true)
    public List<WebhookDeliveryRecord> listDeliveries(WorkspaceAccessContext context, long subscriptionId) {
        workspacePermissionService.requireWebhooksRead(context);
        requireSubscription(context.workspaceId(), subscriptionId);
        return webhookDeliveryStore.findBySubscription(context.workspaceId(), subscriptionId, DEFAULT_LIMIT);
    }

    @Transactional
    public WebhookDeliveryRecord replayDelivery(WorkspaceAccessContext context, long subscriptionId, long deliveryId) {
        workspacePermissionService.requireWebhooksWrite(context);
        requireSubscription(context.workspaceId(), subscriptionId);
        WebhookDeliveryRecord existing = webhookDeliveryStore.findById(context.workspaceId(), subscriptionId, deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook delivery not found: " + deliveryId));
        OffsetDateTime now = OffsetDateTime.now(clock);
        WebhookDeliveryRecord replay = webhookDeliveryStore.create(
                subscriptionId,
                context.workspaceId(),
                existing.eventType(),
                existing.eventId() + ":replay:" + now.toEpochSecond(),
                existing.payload(),
                WebhookDeliveryStatus.PENDING,
                now,
                now);
        securityEventStore.record(
                SecurityEventType.WEBHOOK_DELIVERY_REPLAYED,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                "POST",
                "/api/v1/workspaces/current/webhooks/" + subscriptionId + "/deliveries/" + deliveryId + "/replay",
                null,
                "Webhook delivery replayed",
                now);
        operatorActionLogStore.record(
                context.workspaceId(),
                context.ownerId(),
                "PIPELINE",
                "webhook_delivery_replay",
                null,
                null,
                null,
                "Webhook delivery replayed",
                now);
        return replay;
    }

    private WebhookSubscriptionRecord requireSubscription(long workspaceId, long subscriptionId) {
        return webhookSubscriptionsStore.findById(workspaceId, subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook subscription not found: " + subscriptionId));
    }

    private long enabledWebhookCount(long workspaceId) {
        return webhookSubscriptionsStore.countEnabledByWorkspaceId(workspaceId);
    }

    private void validateCallbackUrl(String callbackUrl) {
        try {
            URI uri = URI.create(callbackUrl);
            String scheme = uri.getScheme();
            if (scheme == null || scheme.isBlank()) {
                throw new InvalidWebhookCallbackUrlException("Webhook callbackUrl must be a valid absolute https URL");
            }
            boolean allowHttpCallbacks = runtimeProperties.getWebhooks().isAllowHttpCallbacks();
            boolean allowPrivateCallbackHosts = runtimeProperties.getWebhooks().isAllowPrivateCallbackHosts();
            boolean https = "https".equalsIgnoreCase(scheme);
            boolean http = "http".equalsIgnoreCase(scheme);
            if (!https && !(http && allowHttpCallbacks)) {
                throw new InvalidWebhookCallbackUrlException("Webhook callbackUrl must use https");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new InvalidWebhookCallbackUrlException("Webhook callbackUrl host is required");
            }
            String host = uri.getHost().trim().toLowerCase(java.util.Locale.ROOT);
            boolean localhost = "localhost".equals(host);
            boolean privateOrLocalHost = localhost || isPrivateOrLocalAddress(host);
            if (privateOrLocalHost && !allowPrivateCallbackHosts) {
                throw new InvalidWebhookCallbackUrlException("Webhook callbackUrl must not target localhost or private addresses");
            }
        } catch (RuntimeException exception) {
            if (exception instanceof InvalidWebhookCallbackUrlException) {
                throw exception;
            }
            throw new InvalidWebhookCallbackUrlException("Webhook callbackUrl must be a valid absolute https URL");
        }
    }

    private boolean isPrivateOrLocalAddress(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress();
        } catch (Exception exception) {
            return false;
        }
    }

    public record CreatedSubscription(WebhookSubscriptionRecord record, String plaintextSecret) {
    }
}
