package com.linkplatform.api.projection;

import java.time.OffsetDateTime;

public record ProjectionJobResponse(
        long id,
        ProjectionJobType jobType,
        ProjectionJobStatus status,
        OffsetDateTime requestedAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        long processedCount,
        Long checkpointId,
        Long driftCount,
        Long repairCount,
        String errorSummary,
        String claimedBy,
        OffsetDateTime claimedUntil) {
}
