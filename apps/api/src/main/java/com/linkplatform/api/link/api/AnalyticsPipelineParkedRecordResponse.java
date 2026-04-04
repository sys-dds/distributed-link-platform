package com.linkplatform.api.link.api;

import java.time.OffsetDateTime;

public record AnalyticsPipelineParkedRecordResponse(
        long id,
        String eventId,
        String eventKey,
        OffsetDateTime createdAt,
        int attemptCount,
        String lastErrorSummary,
        OffsetDateTime parkedAt) {
}
