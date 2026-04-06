package com.linkplatform.api.projection;

import java.time.OffsetDateTime;

public record ProjectionJobResponse(
        long id,
        ProjectionJobType jobType,
        ProjectionJobStatus status,
        OffsetDateTime requestedAt,
        OffsetDateTime startedAt,
        OffsetDateTime lastChunkAt,
        OffsetDateTime completedAt,
        @Deprecated long processedCount,
        long processedItems,
        long failedItems,
        Long checkpointId,
        Long driftCount,
        Long repairCount,
        @Deprecated String errorSummary,
        String lastError,
        String claimedBy,
        OffsetDateTime claimedUntil,
        Long ownerId,
        String slug) {
}
