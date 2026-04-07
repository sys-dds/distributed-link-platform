package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;
import java.util.List;

public interface SecurityEventStore {

    default void record(
            SecurityEventType eventType,
            Long ownerId,
            Long workspaceId,
            String apiKeyHash,
            String requestMethod,
            String requestPath,
            String remoteAddress,
            String detailSummary,
            OffsetDateTime occurredAt) {
        record(eventType, ownerId, apiKeyHash, requestMethod, requestPath, remoteAddress, detailSummary, occurredAt);
    }

    default void record(
            SecurityEventType eventType,
            Long ownerId,
            String apiKeyHash,
            String requestMethod,
            String requestPath,
            String remoteAddress,
            String detailSummary,
            OffsetDateTime occurredAt) {
    }

    default List<SecurityEventRecord> findEvents(long workspaceId, SecurityEventQuery query) {
        return List.of();
    }
}
