package com.linkplatform.api.owner.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.linkplatform.api.owner.application.WebhookDeliveryRecord;
import java.time.OffsetDateTime;

public record WebhookDeliveryResponse(
        long id,
        String eventType,
        String eventId,
        JsonNode payload,
        String status,
        int attemptCount,
        OffsetDateTime nextAttemptAt,
        OffsetDateTime deliveredAt,
        String lastError,
        Integer httpStatus,
        String responseExcerpt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime parkedAt) {

    static WebhookDeliveryResponse from(WebhookDeliveryRecord record) {
        return new WebhookDeliveryResponse(
                record.id(),
                record.eventType().value(),
                record.eventId(),
                record.payload(),
                record.status().name(),
                record.attemptCount(),
                record.nextAttemptAt(),
                record.deliveredAt(),
                record.lastError(),
                record.httpStatus(),
                record.responseExcerpt(),
                record.createdAt(),
                record.updatedAt(),
                record.parkedAt());
    }
}
