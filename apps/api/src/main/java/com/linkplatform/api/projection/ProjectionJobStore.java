package com.linkplatform.api.projection;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ProjectionJobStore {

    ProjectionJob createJob(ProjectionJobType jobType, OffsetDateTime requestedAt);

    Optional<ProjectionJob> findById(long id);

    List<ProjectionJob> findRecent(int limit);

    Optional<ProjectionJob> claimNext(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil);

    void markCompleted(long id, OffsetDateTime completedAt, long processedCount);

    void markFailed(long id, OffsetDateTime completedAt, String errorSummary);

    long countQueued();

    long countActive();
}
