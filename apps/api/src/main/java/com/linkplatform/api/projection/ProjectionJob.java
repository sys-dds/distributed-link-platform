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
        OffsetDateTime claimedUntil,
        Long ownerId,
        String slug) {

    public ProjectionJob(
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
        this(id, jobType, status, requestedAt, startedAt, completedAt, processedCount, checkpointId, errorSummary, claimedBy, claimedUntil, null, null);
    }
}
