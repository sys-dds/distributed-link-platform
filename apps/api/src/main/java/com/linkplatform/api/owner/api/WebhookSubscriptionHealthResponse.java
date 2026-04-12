package com.linkplatform.api.owner.api;

import com.linkplatform.api.owner.application.WebhookSubscriptionRecord;
import java.time.OffsetDateTime;

public record WebhookSubscriptionHealthResponse(
        String verificationStatus,
        OffsetDateTime verifiedAt,
        boolean enabled,
        int consecutiveFailures,
        OffsetDateTime lastDeliveryAt,
        OffsetDateTime lastFailureAt,
        OffsetDateTime disabledAt,
        String disabledReason,
        OffsetDateTime lastTestFiredAt,
        Long lastTestDeliveryId,
        int eventVersion) {

    static WebhookSubscriptionHealthResponse from(WebhookSubscriptionRecord record) {
        return new WebhookSubscriptionHealthResponse(
                record.verificationStatus(),
                record.verifiedAt(),
                record.enabled(),
                record.consecutiveFailures(),
                record.lastDeliveryAt(),
                record.lastFailureAt(),
                record.disabledAt(),
                record.disabledReason(),
                record.lastTestFiredAt(),
                record.lastTestDeliveryId(),
                record.eventVersion());
    }
}
