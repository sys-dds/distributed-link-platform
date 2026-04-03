package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;

public record AnalyticsOutboxRecord(
        long id,
        String eventId,
        String eventType,
        String eventKey,
        String payloadJson,
        OffsetDateTime createdAt,
        OffsetDateTime publishedAt) {
}
