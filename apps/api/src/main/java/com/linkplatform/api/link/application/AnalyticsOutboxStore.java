package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;
import java.util.List;

public interface AnalyticsOutboxStore {

    void saveRedirectClickEvent(RedirectClickAnalyticsEvent redirectClickAnalyticsEvent);

    long countUnpublished();

    long countEligible(OffsetDateTime now);

    long countParked();

    Double findOldestEligibleAgeSeconds(OffsetDateTime now);

    default Double findOldestParkedAgeSeconds(OffsetDateTime now) {
        return null;
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

    default int requeueAllParked(int limit, OffsetDateTime nextAttemptAt) {
        return 0;
    }

    long archivePublishedBefore(OffsetDateTime cutoff, int limit);

    long countArchived();
}
