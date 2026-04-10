package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;
import java.util.List;

public interface LinkLifecycleOutboxStore {

    void saveLinkLifecycleEvent(LinkLifecycleEvent linkLifecycleEvent);

    long countUnpublished();

    default long countEligible() {
        throw new UnsupportedOperationException("countEligible() requires store-specific clock handling");
    }

    default long countEligible(OffsetDateTime now) {
        return countEligible();
    }

    long countParked();

    default OffsetDateTime findOldestEligibleAt() {
        return null;
    }

    default Double findOldestEligibleAgeSeconds(OffsetDateTime now) {
        OffsetDateTime oldest = findOldestEligibleAt();
        return oldest == null ? null : (double) java.time.Duration.between(oldest, now).toSeconds();
    }

    default OffsetDateTime findOldestParkedAt() {
        return null;
    }

    default Double findOldestParkedAgeSeconds(OffsetDateTime now) {
        OffsetDateTime oldest = findOldestParkedAt();
        return oldest == null ? null : (double) java.time.Duration.between(oldest, now).toSeconds();
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

    default List<LinkLifecycleHistoryRecord> findHistoryChunkAfter(
            long afterId,
            int limit,
            Long workspaceId,
            Long ownerId,
            String slug,
            OffsetDateTime from,
            OffsetDateTime to) {
        return findHistoryChunkAfter(afterId, limit, ownerId, slug);
    }

    List<LinkLifecycleOutboxRecord> findParked(int limit);

    boolean requeueParked(long id, OffsetDateTime nextAttemptAt);

    default int requeueParkedBatch(List<Long> ids, OffsetDateTime nextAttemptAt) {
        return 0;
    }

    default int requeueAllParked(int limit) {
        return 0;
    }

    default int requeueAllParked(int limit, OffsetDateTime nextAttemptAt) {
        return 0;
    }
}
