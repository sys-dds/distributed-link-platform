package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;
import java.util.List;

public interface AnalyticsOutboxStore {

    void saveRedirectClickEvent(RedirectClickAnalyticsEvent redirectClickAnalyticsEvent);

    long countUnpublished();

    List<AnalyticsOutboxRecord> claimBatch(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil, int limit);

    void markPublished(long id, OffsetDateTime publishedAt);
}
