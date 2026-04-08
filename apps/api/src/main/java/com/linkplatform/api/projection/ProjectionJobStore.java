package com.linkplatform.api.projection;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ProjectionJobStore {

    ProjectionJob createJob(ProjectionJobType jobType, OffsetDateTime requestedAt);

    ProjectionJob createJob(ProjectionJobType jobType, OffsetDateTime requestedAt, Long ownerId, String slug);

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
    List<ProjectionJob> findRecent(int limit);

    @Deprecated(forRemoval = false)
    Optional<ProjectionJob> findById(long id);

    Optional<ProjectionJob> claimNext(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil);

    @Deprecated(forRemoval = false)
    Optional<ProjectionJob> claimNextQueued(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil);

    void markProgress(long id, OffsetDateTime occurredAt, long processedCountIncrement, Long checkpointId);

    @Deprecated(forRemoval = false)
    void markProgress(long id, long processedCountIncrement, Long checkpointId);

    void markCompleted(long id, OffsetDateTime completedAt, long processedCountIncrement, Long checkpointId);

    void markFailed(long id, OffsetDateTime completedAt, long failedItemsIncrement, String errorSummary);

    void markFailed(long id, OffsetDateTime completedAt, String errorSummary);

    long countQueued();

    long countActive();

    long countQueued(long workspaceId);

    long countActive(long workspaceId);

    long countFailed(long workspaceId);

    long countCompleted(long workspaceId);

    Optional<OffsetDateTime> findLatestStartedAt(long workspaceId);

    Optional<OffsetDateTime> findLatestFailedAt(long workspaceId);
}
