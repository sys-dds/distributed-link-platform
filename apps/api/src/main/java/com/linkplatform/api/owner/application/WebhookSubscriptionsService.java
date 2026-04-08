package com.linkplatform.api.owner.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.linkplatform.api.runtime.LinkPlatformRuntimeProperties;
import java.net.InetAddress;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final WebhookDispatcher webhookDispatcher;
    private final ObjectMapper objectMapper;
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
            WebhookDispatcher webhookDispatcher,
            ObjectMapper objectMapper,
            LinkPlatformRuntimeProperties runtimeProperties) {
        this.webhookSubscriptionsStore = webhookSubscriptionsStore;
        this.webhookDeliveryStore = webhookDeliveryStore;
        this.workspaceEntitlementService = workspaceEntitlementService;
        this.workspacePermissionService = workspacePermissionService;
        this.securityEventStore = securityEventStore;
        this.operatorActionLogStore = operatorActionLogStore;
        this.webhookSigningService = webhookSigningService;
        this.webhookDispatcher = webhookDispatcher;
        this.objectMapper = objectMapper;
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
            enforceEnabledWebhookQuota(context.workspaceId());
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
            enforceEnabledWebhookQuota(context.workspaceId());
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

    @Transactional
    public VerificationAttempt verifySubscription(WorkspaceAccessContext context, long subscriptionId) {
        workspacePermissionService.requireWebhooksWrite(context);
        WebhookSubscriptionRecord subscription = requireSubscription(context.workspaceId(), subscriptionId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        WebhookDeliveryRecord delivery = webhookDeliveryStore.create(
                subscriptionId,
                context.workspaceId(),
                WebhookEventType.WEBHOOK_VERIFICATION,
                "webhook-verification:" + subscriptionId + ":" + now.toEpochSecond(),
                verificationPayload(subscription, now),
                WebhookDeliveryStatus.PENDING,
                now,
                now);
        WebhookDispatcher.DispatchResult result = webhookDispatcher.dispatch(new WebhookDeliveryStore.DispatchItem(delivery, subscription));
        if (isSuccessful(result)) {
            webhookDeliveryStore.markDelivered(delivery.id(), now, result.httpStatus(), result.responseBody());
            WebhookSubscriptionRecord verified = webhookSubscriptionsStore.markVerified(context.workspaceId(), subscriptionId, now);
            webhookSubscriptionsStore.recordDeliverySuccess(context.workspaceId(), subscriptionId, now);
            securityEventStore.record(
                    SecurityEventType.WEBHOOK_SUBSCRIPTION_VERIFIED,
                    context.ownerId(),
                    context.workspaceId(),
                    context.apiKeyHash(),
                    "POST",
                    "/api/v1/workspaces/current/webhooks/" + subscriptionId + "/verify",
                    null,
                    "Webhook subscription verified",
                    now);
            return new VerificationAttempt(verified, delivery.id(), true, result.httpStatus(), null);
        }
        int attemptCount = delivery.attemptCount() + 1;
        String failure = dispatchFailure(result);
        webhookDeliveryStore.markFailed(
                delivery.id(),
                attemptCount,
                null,
                result.httpStatus(),
                failure,
                result.responseBody());
        webhookSubscriptionsStore.incrementFailureCount(context.workspaceId(), subscriptionId, now);
        return new VerificationAttempt(
                requireSubscription(context.workspaceId(), subscriptionId),
                delivery.id(),
                false,
                result.httpStatus(),
                failure);
    }

    @Transactional
    public WebhookDeliveryRecord testFireSubscription(WorkspaceAccessContext context, long subscriptionId) {
        workspacePermissionService.requireWebhooksWrite(context);
        WebhookSubscriptionRecord subscription = requireSubscription(context.workspaceId(), subscriptionId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        WebhookDeliveryRecord delivery = webhookDeliveryStore.create(
                subscriptionId,
                context.workspaceId(),
                WebhookEventType.WEBHOOK_TEST_FIRED,
                "webhook-test:" + subscriptionId + ":" + now.toEpochSecond(),
                testPayload(subscription, now),
                WebhookDeliveryStatus.PENDING,
                now,
                now);
        webhookSubscriptionsStore.recordTestFired(context.workspaceId(), subscriptionId, delivery.id(), now);
        securityEventStore.record(
                SecurityEventType.WEBHOOK_TEST_FIRED,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                "POST",
                "/api/v1/workspaces/current/webhooks/" + subscriptionId + "/test-fire",
                null,
                "Webhook test fired",
                now);
        operatorActionLogStore.record(
                context.workspaceId(),
                context.ownerId(),
                "PIPELINE",
                "webhook_test_fire",
                null,
                null,
                null,
                "Webhook test fired",
                now);
        return delivery;
    }

    @Transactional(readOnly = true)
    public WebhookSubscriptionRecord subscriptionHealth(WorkspaceAccessContext context, long subscriptionId) {
        workspacePermissionService.requireWebhooksRead(context);
        requireSubscription(context.workspaceId(), subscriptionId);
        return requireSubscription(context.workspaceId(), subscriptionId);
    }

    private WebhookSubscriptionRecord requireSubscription(long workspaceId, long subscriptionId) {
        return webhookSubscriptionsStore.findById(workspaceId, subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook subscription not found: " + subscriptionId));
    }

    private long enabledWebhookCount(long workspaceId) {
        return webhookSubscriptionsStore.countEnabledByWorkspaceId(workspaceId);
    }

    private void enforceEnabledWebhookQuota(long workspaceId) {
        // Create and enable flows both delegate to the same quota path so HTTP failures stay structurally identical.
        workspaceEntitlementService.enforceWebhooksQuota(workspaceId, enabledWebhookCount(workspaceId));
    }

    private void validateCallbackUrl(String callbackUrl) {
        try {
            URI uri = URI.create(callbackUrl);
            String scheme = uri.getScheme();
            if (scheme == null || scheme.isBlank()) {
                throw new InvalidWebhookCallbackUrlException("Webhook callbackUrl must be a valid absolute https URL");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new InvalidWebhookCallbackUrlException("Webhook callbackUrl host is required");
            }
            validateCallbackScheme(scheme);
            String host = uri.getHost().trim().toLowerCase(java.util.Locale.ROOT);
            validateCallbackHost(host);
        } catch (RuntimeException exception) {
            if (exception instanceof InvalidWebhookCallbackUrlException) {
                throw exception;
            }
            throw new InvalidWebhookCallbackUrlException("Webhook callbackUrl must be a valid absolute https URL");
        }
    }

    private void validateCallbackScheme(String scheme) {
        boolean https = "https".equalsIgnoreCase(scheme);
        boolean http = "http".equalsIgnoreCase(scheme);
        boolean httpAllowed = runtimeProperties.getWebhooks().isAllowHttpCallbacks();
        // Scheme and host validation are intentionally separate: allow-http-callbacks only relaxes the scheme
        // requirement, and it does not make localhost/private hosts legal on its own.
        if (!https && !(http && httpAllowed)) {
            throw new InvalidWebhookCallbackUrlException("Webhook callbackUrl must use https");
        }
    }

    private void validateCallbackHost(String host) {
        boolean localhost = "localhost".equals(host);
        boolean privateOrLocalHost = localhost || isPrivateOrLocalAddress(host);
        boolean privateHostsAllowed = runtimeProperties.getWebhooks().isAllowPrivateCallbackHosts();
        // Host safety stays strict unless allow-private-callback-hosts is explicitly enabled. This does not relax
        // the scheme requirement, which has already been checked above.
        if (privateOrLocalHost && !privateHostsAllowed) {
            throw new InvalidWebhookCallbackUrlException("Webhook callbackUrl must not target localhost or private addresses");
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

    private boolean isSuccessful(WebhookDispatcher.DispatchResult result) {
        return result.httpStatus() != null && result.httpStatus() >= 200 && result.httpStatus() < 300;
    }

    private String dispatchFailure(WebhookDispatcher.DispatchResult result) {
        return result.transportError() != null ? result.transportError() : "HTTP " + result.httpStatus();
    }

    private JsonNode verificationPayload(WebhookSubscriptionRecord subscription, OffsetDateTime occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventVersion", subscription.eventVersion());
        payload.put("type", WebhookEventType.WEBHOOK_VERIFICATION.value());
        payload.put("workspaceSlug", subscription.workspaceSlug());
        payload.put("subscriptionId", subscription.id());
        payload.put("verificationStatus", "PENDING");
        payload.put("occurredAt", occurredAt);
        return objectMapper.valueToTree(payload);
    }

    private JsonNode testPayload(WebhookSubscriptionRecord subscription, OffsetDateTime occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventVersion", subscription.eventVersion());
        payload.put("type", WebhookEventType.WEBHOOK_TEST_FIRED.value());
        payload.put("workspaceSlug", subscription.workspaceSlug());
        payload.put("subscriptionId", subscription.id());
        payload.put("test", true);
        payload.put("occurredAt", occurredAt);
        return objectMapper.valueToTree(payload);
    }

    public record CreatedSubscription(WebhookSubscriptionRecord record, String plaintextSecret) {
    }

    public record VerificationAttempt(
            WebhookSubscriptionRecord record,
            long deliveryId,
            boolean verified,
            Integer httpStatus,
            String failureReason) {
    }
}
