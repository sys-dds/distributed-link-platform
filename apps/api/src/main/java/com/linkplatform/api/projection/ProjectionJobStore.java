package com.linkplatform.api.projection;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ProjectionJobStore {

    default ProjectionJob createJob(ProjectionJobType jobType, OffsetDateTime requestedAt) {
        return createJob(jobType, requestedAt, null, null, null, null, null, null, null);
    }

    default ProjectionJob createJob(ProjectionJobType jobType, OffsetDateTime requestedAt, Long ownerId, String slug) {
        return createJob(jobType, requestedAt, ownerId, null, slug, null, null, null, null);
    }

    ProjectionJob createJob(
            ProjectionJobType jobType,
            OffsetDateTime requestedAt,
            Long ownerId,
            Long workspaceId,
            String slug,
            OffsetDateTime rangeStart,
            OffsetDateTime rangeEnd,
            Long requestedByOwnerId,
            String operatorNote);

    Optional<ProjectionJob> findByIdVisibleToWorkspace(long id, long workspaceId, long ownerId, boolean personalWorkspace);

    List<ProjectionJob> findRecentVisibleToWorkspace(int limit, long workspaceId, long ownerId, boolean personalWorkspace);

    @Deprecated(forRemoval = false)
    default List<ProjectionJob> findRecent(int limit) {
        return List.of();
    }

    @Deprecated(forRemoval = false)
    default Optional<ProjectionJob> findById(long id) {
        throw new UnsupportedOperationException("Use findByIdVisibleToWorkspace for workspace-scoped reads");
    }

    default Optional<ProjectionJob> claimNext(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil) {
        return Optional.empty();
    }

    @Deprecated(forRemoval = false)
    default Optional<ProjectionJob> claimNextQueued(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil) {
        return claimNext(workerId, now, claimedUntil);
    }

    default void markProgress(long id, OffsetDateTime occurredAt, long processedCountIncrement, Long checkpointId) {
    }

    @Deprecated(forRemoval = false)
    default void markProgress(long id, long processedCountIncrement, Long checkpointId) {
        markProgress(id, OffsetDateTime.now(), processedCountIncrement, checkpointId);
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
