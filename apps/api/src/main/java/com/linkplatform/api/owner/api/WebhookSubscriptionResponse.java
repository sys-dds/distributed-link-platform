package com.linkplatform.api.owner.api;

import com.linkplatform.api.owner.application.WebhookSubscriptionRecord;
import java.time.OffsetDateTime;
import java.util.List;

public record WebhookSubscriptionResponse(
        long id,
        String name,
        String callbackUrl,
        String secretPrefix,
        boolean enabled,
        List<String> eventTypes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime lastDeliveryAt,
        OffsetDateTime lastFailureAt,
        int consecutiveFailures,
        OffsetDateTime disabledAt,
        String disabledReason) {

    static WebhookSubscriptionResponse from(WebhookSubscriptionRecord record) {
        return new WebhookSubscriptionResponse(
                record.id(),
                record.name(),
                record.callbackUrl(),
                record.signingSecretPrefix(),
                record.enabled(),
                record.eventTypes().stream().map(type -> type.value()).sorted().toList(),
                record.createdAt(),
                record.updatedAt(),
                record.lastDeliveryAt(),
                record.lastFailureAt(),
                record.consecutiveFailures(),
                record.disabledAt(),
                record.disabledReason());
    }
}
