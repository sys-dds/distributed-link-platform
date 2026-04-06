package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;
import java.util.List;

public interface AnalyticsOutboxStore {

    void saveRedirectClickEvent(RedirectClickAnalyticsEvent redirectClickAnalyticsEvent);

    long countUnpublished();

    default long countEligible() {
        return countEligible(OffsetDateTime.now(java.time.Clock.systemUTC()));
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

    List<AnalyticsOutboxRecord> claimBatch(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil, int limit);

    void markPublished(long id, OffsetDateTime publishedAt);

    void recordPublishFailure(
            long id,
            int attemptCount,
            OffsetDateTime nextAttemptAt,
            String lastErrorSummary,
            OffsetDateTime parkedAt);

    List<AnalyticsOutboxRecord> findParked(int limit);

    boolean requeueParked(long id, OffsetDateTime nextAttemptAt);

    default int requeueParkedBatch(List<Long> ids, OffsetDateTime nextAttemptAt) {
        return 0;
    }

    default int requeueAllParked(int limit) {
        return requeueAllParked(limit, OffsetDateTime.now(java.time.Clock.systemUTC()));
    }

    default int requeueAllParked(int limit, OffsetDateTime nextAttemptAt) {
        return 0;
    }

    long archivePublishedBefore(OffsetDateTime cutoff, int limit);

    long countArchived();
}
