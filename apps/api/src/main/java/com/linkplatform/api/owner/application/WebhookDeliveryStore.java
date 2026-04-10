package com.linkplatform.api.owner.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface WebhookDeliveryStore {

    WebhookDeliveryRecord create(
            long subscriptionId,
            long workspaceId,
            WebhookEventType eventType,
            String eventId,
            JsonNode payload,
            WebhookDeliveryStatus status,
            OffsetDateTime createdAt,
            OffsetDateTime nextAttemptAt);

    Optional<WebhookDeliveryRecord> findBySubscriptionAndEventId(long subscriptionId, String eventId);

    List<WebhookDeliveryRecord> findBySubscription(long workspaceId, long subscriptionId, int limit);

    Optional<WebhookDeliveryRecord> findById(long workspaceId, long subscriptionId, long deliveryId);

    Optional<WebhookDeliveryRecord> findLatestBySubscription(long workspaceId, long subscriptionId);

    List<DispatchItem> claimDueDeliveries(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil, int limit);

    void markDelivered(long deliveryId, OffsetDateTime deliveredAt, Integer httpStatus, String responseExcerpt);

    void markFailed(long deliveryId, int attemptCount, OffsetDateTime nextAttemptAt, Integer httpStatus, String lastError, String responseExcerpt);

    void markParked(long deliveryId, int attemptCount, String lastError, Integer httpStatus, String responseExcerpt, OffsetDateTime parkedAt);

    void markDisabled(long deliveryId, String reason, OffsetDateTime updatedAt);

    int parkDueDeliveriesForWorkspace(long workspaceId, String reason, OffsetDateTime parkedAt);

    int disableQueuedDeliveriesForSubscription(long workspaceId, long subscriptionId, String reason, OffsetDateTime updatedAt);

    default int currentEventVersion() {
        return WebhookEventType.CURRENT_EVENT_VERSION;
    }

    record DispatchItem(
            WebhookDeliveryRecord delivery,
            WebhookSubscriptionRecord subscription) {
    }
}
