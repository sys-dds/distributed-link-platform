package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;

public record LinkLifecycleOutboxRecord(
        long id,
        String eventId,
        String eventType,
        String eventKey,
        String payloadJson,
        OffsetDateTime createdAt,
        OffsetDateTime publishedAt,
        String claimedBy,
        OffsetDateTime claimedUntil,
        int attemptCount,
        OffsetDateTime nextAttemptAt,
        String lastErrorSummary,
        OffsetDateTime parkedAt) {
}
