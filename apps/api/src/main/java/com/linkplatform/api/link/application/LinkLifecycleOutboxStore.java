package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;
import java.util.List;

public interface LinkLifecycleOutboxStore {

    void saveLinkLifecycleEvent(LinkLifecycleEvent linkLifecycleEvent);

    long countUnpublished();

    long countEligible(OffsetDateTime now);

    long countParked();

    Double findOldestEligibleAgeSeconds(OffsetDateTime now);

    default Double findOldestParkedAgeSeconds(OffsetDateTime now) {
        return null;
    }

    List<LinkLifecycleOutboxRecord> claimBatch(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil, int limit);

    void markPublished(long id, OffsetDateTime publishedAt);

    void recordPublishFailure(
            long id,
            int attemptCount,
            OffsetDateTime nextAttemptAt,
            String lastErrorSummary,
            OffsetDateTime parkedAt);

    List<LinkLifecycleEvent> findAllHistory();

    List<LinkLifecycleHistoryRecord> findHistoryChunkAfter(long afterId, int limit);

    default List<LinkLifecycleHistoryRecord> findHistoryChunkAfter(long afterId, int limit, Long ownerId, String slug) {
        return findHistoryChunkAfter(afterId, limit);
    }

    List<LinkLifecycleOutboxRecord> findParked(int limit);

    boolean requeueParked(long id, OffsetDateTime nextAttemptAt);

    default int requeueParkedBatch(List<Long> ids, OffsetDateTime nextAttemptAt) {
        return 0;
    }

    default int requeueAllParked(int limit, OffsetDateTime nextAttemptAt) {
        return 0;
    }
}
