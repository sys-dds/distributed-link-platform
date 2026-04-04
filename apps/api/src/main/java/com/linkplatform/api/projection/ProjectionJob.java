package com.linkplatform.api.projection;

import java.time.OffsetDateTime;

public record ProjectionJob(
        long id,
        ProjectionJobType jobType,
        ProjectionJobStatus status,
        OffsetDateTime requestedAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        long processedCount,
        Long checkpointId,
        String errorSummary,
        String claimedBy,
        OffsetDateTime claimedUntil) {
}
