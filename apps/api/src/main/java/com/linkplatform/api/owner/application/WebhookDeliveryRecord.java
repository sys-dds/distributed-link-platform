package com.linkplatform.api.owner.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

public record WebhookDeliveryRecord(
        long id,
        long subscriptionId,
        long workspaceId,
        String workspaceSlug,
        WebhookEventType eventType,
        String eventId,
        JsonNode payload,
        WebhookDeliveryStatus status,
        int attemptCount,
        OffsetDateTime nextAttemptAt,
        OffsetDateTime deliveredAt,
        String lastError,
        Integer httpStatus,
        String responseExcerpt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime parkedAt) {
}
