package com.linkplatform.api.owner.api;

import java.time.OffsetDateTime;

public record TestWebhookSubscriptionResponse(
        long deliveryId,
        OffsetDateTime createdAt,
        int eventVersion) {
}
