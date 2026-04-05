package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;

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
}
