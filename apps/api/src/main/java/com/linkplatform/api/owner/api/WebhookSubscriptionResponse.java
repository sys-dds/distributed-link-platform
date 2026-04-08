package com.linkplatform.api.owner.api;

import com.linkplatform.api.owner.application.WebhookSubscriptionRecord;
import java.time.OffsetDateTime;
import java.util.List;

public record WebhookSubscriptionResponse(
        long id,
        String name,
        String callbackUrl,
        String secretPrefix,
        int eventVersion,
        String verificationStatus,
        OffsetDateTime verifiedAt,
        boolean enabled,
        List<String> eventTypes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime lastDeliveryAt,
        OffsetDateTime lastFailureAt,
        int consecutiveFailures,
        OffsetDateTime disabledAt,
        String disabledReason,
        OffsetDateTime lastTestFiredAt,
        Long lastTestDeliveryId) {

    static WebhookSubscriptionResponse from(WebhookSubscriptionRecord record) {
        return new WebhookSubscriptionResponse(
                record.id(),
                record.name(),
                record.callbackUrl(),
                record.signingSecretPrefix(),
                record.eventVersion(),
                record.verificationStatus(),
                record.verifiedAt(),
                record.enabled(),
                record.eventTypes().stream().map(type -> type.value()).sorted().toList(),
                record.createdAt(),
                record.updatedAt(),
                record.lastDeliveryAt(),
                record.lastFailureAt(),
                record.consecutiveFailures(),
                record.disabledAt(),
                record.disabledReason(),
                record.lastTestFiredAt(),
                record.lastTestDeliveryId());
    }
}
