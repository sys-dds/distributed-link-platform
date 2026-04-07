package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;
import java.util.Set;

public record WebhookSubscriptionRecord(
        long id,
        long workspaceId,
        String workspaceSlug,
        String name,
        String callbackUrl,
        String signingSecretHash,
        String signingSecretPrefix,
        boolean enabled,
        Set<WebhookEventType> eventTypes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime lastDeliveryAt,
        OffsetDateTime lastFailureAt,
        int consecutiveFailures,
        OffsetDateTime disabledAt,
        String disabledReason) {
}
