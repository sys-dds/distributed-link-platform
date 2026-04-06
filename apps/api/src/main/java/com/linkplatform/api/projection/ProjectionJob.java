package com.linkplatform.api.projection;

import java.time.OffsetDateTime;

public record ProjectionJob(
        long id,
        ProjectionJobType jobType,
        ProjectionJobStatus status,
        OffsetDateTime requestedAt,
        OffsetDateTime startedAt,
        OffsetDateTime lastChunkAt,
        OffsetDateTime completedAt,
        long processedCount,
        long processedItems,
        long failedItems,
        Long checkpointId,
        String errorSummary,
        String lastError,
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
            OffsetDateTime lastChunkAt,
            OffsetDateTime completedAt,
            long processedCount,
            long processedItems,
            long failedItems,
            Long checkpointId,
            String errorSummary,
            String lastError,
            String claimedBy,
            OffsetDateTime claimedUntil) {
        this(
                id,
                jobType,
                status,
                requestedAt,
                startedAt,
                lastChunkAt,
                completedAt,
                processedCount,
                processedItems,
                failedItems,
                checkpointId,
                errorSummary,
                lastError,
                claimedBy,
                claimedUntil,
                null,
                null);
    }

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
            OffsetDateTime claimedUntil,
            Long ownerId,
            String slug) {
        this(
                id,
                jobType,
                status,
                requestedAt,
                startedAt,
                null,
                completedAt,
                processedCount,
                processedCount,
                0L,
                checkpointId,
                errorSummary,
                errorSummary,
                claimedBy,
                claimedUntil,
                ownerId,
                slug);
    }

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
