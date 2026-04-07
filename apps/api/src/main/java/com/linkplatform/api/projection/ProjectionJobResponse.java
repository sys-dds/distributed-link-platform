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

    static ProjectionJobResponse from(ProjectionJob job) {
        return canonical(
                job.id(),
                job.jobType(),
                job.status(),
                job.requestedAt(),
                job.startedAt(),
                job.lastChunkAt(),
                job.completedAt(),
                job.processedItems(),
                job.failedItems(),
                job.checkpointId(),
                null,
                null,
                job.lastError(),
                job.claimedBy(),
                job.claimedUntil(),
                job.ownerId(),
                job.slug());
    }

    private static ProjectionJobResponse canonical(
            long id,
            ProjectionJobType jobType,
            ProjectionJobStatus status,
            OffsetDateTime requestedAt,
            OffsetDateTime startedAt,
            OffsetDateTime lastChunkAt,
            OffsetDateTime completedAt,
            long processedItems,
            long failedItems,
            Long checkpointId,
            Long driftCount,
            Long repairCount,
            String lastError,
            String claimedBy,
            OffsetDateTime claimedUntil,
            Long ownerId,
            String slug) {
        return new ProjectionJobResponse(
                id,
                jobType,
                status,
                requestedAt,
                startedAt,
                lastChunkAt,
                completedAt,
                compatibilityProcessedCount(processedItems),
                processedItems,
                failedItems,
                checkpointId,
                driftCount,
                repairCount,
                compatibilityErrorSummary(lastError),
                lastError,
                claimedBy,
                claimedUntil,
                ownerId,
                slug);
    }

    private static long compatibilityProcessedCount(long processedItems) {
        return processedItems;
    }

    private static String compatibilityErrorSummary(String lastError) {
        return lastError;
    }
}
