package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;
import java.util.List;

public interface LinkLifecycleOutboxStore {

    void saveLinkLifecycleEvent(LinkLifecycleEvent linkLifecycleEvent);

    long countUnpublished();

    long countEligible(OffsetDateTime now);

    long countParked();

    Double findOldestEligibleAgeSeconds(OffsetDateTime now);

    List<LinkLifecycleOutboxRecord> claimBatch(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil, int limit);

    void markPublished(long id, OffsetDateTime publishedAt);

    void recordPublishFailure(
            long id,
            int attemptCount,
            OffsetDateTime nextAttemptAt,
            String lastErrorSummary,
            OffsetDateTime parkedAt);

    List<LinkLifecycleEvent> findAllHistory();

    List<LinkLifecycleOutboxRecord> findParked(int limit);

    boolean requeueParked(long id, OffsetDateTime nextAttemptAt);
}
