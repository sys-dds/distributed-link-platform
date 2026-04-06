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

    Optional<ProjectionJob> findById(long id);

    List<ProjectionJob> findRecent(int limit);

    default Optional<ProjectionJob> claimNext(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil) {
        return claimNextQueued(workerId, now, claimedUntil);
    }

    default Optional<ProjectionJob> claimNextQueued(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil) {
        return Optional.empty();
    }

    void markProgress(long id, long processedCountIncrement, Long checkpointId);

    void markCompleted(long id, OffsetDateTime completedAt, long processedCountIncrement, Long checkpointId);

    void markFailed(long id, OffsetDateTime completedAt, String errorSummary);

    default long countQueued() {
        return 0L;
    }

    default long countActive() {
        return 0L;
    }
}
