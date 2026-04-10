package com.linkplatform.api.projection;

import com.linkplatform.api.owner.application.WorkspaceAccessContext;
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
        String workspaceSlug,
        String slug,
        OffsetDateTime from,
        OffsetDateTime to,
        Long requestedByOwnerId,
        String operatorNote) {

    private static final Long DRIFT_COUNT_UNAVAILABLE = null;
    private static final Long REPAIR_COUNT_UNAVAILABLE = null;

    public static ProjectionJobResponse from(ProjectionJob job, WorkspaceAccessContext context) {
        String resolvedWorkspaceSlug = normalizeWorkspaceSlug(context.workspaceSlug());
        return new ProjectionJobResponse(
                job.id(),
                job.jobType(),
                job.status(),
                job.requestedAt(),
                job.startedAt(),
                job.lastChunkAt(),
                job.completedAt(),
                job.processedItems(),
                job.processedItems(),
                job.failedItems(),
                job.checkpointId(),
                DRIFT_COUNT_UNAVAILABLE,
                REPAIR_COUNT_UNAVAILABLE,
                job.lastError(),
                job.lastError(),
                job.claimedBy(),
                job.claimedUntil(),
                job.ownerId(),
                resolvedWorkspaceSlug,
                job.slug(),
                job.rangeStart(),
                job.rangeEnd(),
                job.requestedByOwnerId(),
                job.operatorNote());
    }

    private static String normalizeWorkspaceSlug(String workspaceSlug) {
        if (workspaceSlug == null || workspaceSlug.isBlank()) {
            return null;
        }
        return workspaceSlug;
    }
}
