package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;
import java.util.List;

public interface SecurityEventStore {

    void record(
            SecurityEventType eventType,
            Long ownerId,
            String apiKeyHash,
            String requestMethod,
            String requestPath,
            String remoteAddress,
            String detailSummary,
            OffsetDateTime occurredAt);

    default List<SecurityEventRecord> findEvents(long ownerId, SecurityEventQuery query) {
        return List.of();
    }
}
