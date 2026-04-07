package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface WebhookSubscriptionsStore {

    List<WebhookSubscriptionRecord> findByWorkspaceId(long workspaceId);

    Optional<WebhookSubscriptionRecord> findById(long workspaceId, long subscriptionId);

    WebhookSubscriptionRecord create(
            long workspaceId,
            String name,
            String callbackUrl,
            String signingSecretHash,
            String signingSecretPrefix,
            boolean enabled,
            Set<WebhookEventType> eventTypes,
            OffsetDateTime createdAt);

    WebhookSubscriptionRecord update(
            long workspaceId,
            long subscriptionId,
            String name,
            Boolean enabled,
            Set<WebhookEventType> eventTypes,
            OffsetDateTime updatedAt);

    WebhookSubscriptionRecord rotateSecret(
            long workspaceId,
            long subscriptionId,
            String signingSecretHash,
            String signingSecretPrefix,
            OffsetDateTime updatedAt);

    long countEnabledByWorkspaceId(long workspaceId);

    List<WebhookSubscriptionRecord> findEnabledByWorkspaceIdAndEventType(long workspaceId, WebhookEventType eventType);

    void recordDeliverySuccess(long workspaceId, long subscriptionId, OffsetDateTime deliveredAt);

    int incrementFailureCount(long workspaceId, long subscriptionId, OffsetDateTime failedAt);

    void disable(long workspaceId, long subscriptionId, String reason, OffsetDateTime disabledAt);
}
