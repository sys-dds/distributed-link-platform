package com.linkplatform.api.projection;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ProjectionJobStore {

    ProjectionJob createJob(ProjectionJobType jobType, OffsetDateTime requestedAt);

    default ProjectionJob createJob(ProjectionJobType jobType, OffsetDateTime requestedAt, Long ownerId, String slug) {
        if (ownerId != null || slug != null) {
            throw new UnsupportedOperationException("Scoped projection jobs are not supported");
        }
        return createJob(jobType, requestedAt);
    }

    default ProjectionJob createJob(
            ProjectionJobType jobType,
            OffsetDateTime requestedAt,
            Long ownerId,
            Long workspaceId,
            String slug,
            OffsetDateTime rangeStart,
            OffsetDateTime rangeEnd,
            Long requestedByOwnerId,
            String operatorNote) {
        if (workspaceId != null || rangeStart != null || rangeEnd != null || requestedByOwnerId != null || operatorNote != null) {
            throw new UnsupportedOperationException("Extended scoped projection jobs are not supported");
        }
        return createJob(jobType, requestedAt, ownerId, slug);
    }

    Optional<ProjectionJob> findById(long id);

    List<ProjectionJob> findRecent(int limit);

    default Optional<ProjectionJob> claimNext(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil) {
        return claimNextQueued(workerId, now, claimedUntil);
    }

    default Optional<ProjectionJob> claimNextQueued(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil) {
        return Optional.empty();
    }

    default void markProgress(long id, OffsetDateTime occurredAt, long processedCountIncrement, Long checkpointId) {
        markProgress(id, processedCountIncrement, checkpointId);
    }

    default void markProgress(long id, long processedCountIncrement, Long checkpointId) {
        markProgress(id, OffsetDateTime.now(java.time.Clock.systemUTC()), processedCountIncrement, checkpointId);
    }

    void markCompleted(long id, OffsetDateTime completedAt, long processedCountIncrement, Long checkpointId);

    void markFailed(long id, OffsetDateTime completedAt, long failedItemsIncrement, String errorSummary);

    default void markFailed(long id, OffsetDateTime completedAt, String errorSummary) {
        markFailed(id, completedAt, 0L, errorSummary);
    }

    default long countQueued() {
        return 0L;
    }

    default long countActive() {
        return 0L;
    }

    default long countQueued(Long workspaceId) {
        return countQueued();
    }

    default long countActive(Long workspaceId) {
        return countActive();
    }

    default long countFailed(Long workspaceId) {
        return 0L;
    }

    default long countCompleted(Long workspaceId) {
        return 0L;
    }

    default Optional<OffsetDateTime> findLatestStartedAt(Long workspaceId) {
        return Optional.empty();
    }

    default Optional<OffsetDateTime> findLatestFailedAt(Long workspaceId) {
        return Optional.empty();
    }
}
