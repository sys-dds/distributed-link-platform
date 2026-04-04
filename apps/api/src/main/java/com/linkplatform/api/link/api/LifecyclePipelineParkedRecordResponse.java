package com.linkplatform.api.link.api;

import java.time.OffsetDateTime;

public record LifecyclePipelineParkedRecordResponse(
        long id,
        String eventId,
        String eventType,
        String eventKey,
        OffsetDateTime createdAt,
        int attemptCount,
        String lastErrorSummary,
        OffsetDateTime parkedAt) {
}
