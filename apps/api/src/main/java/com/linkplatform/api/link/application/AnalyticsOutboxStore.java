package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;
import java.util.List;

public interface AnalyticsOutboxStore {

    void saveRedirectClickEvent(RedirectClickAnalyticsEvent redirectClickAnalyticsEvent);

    List<AnalyticsOutboxRecord> findUnpublished(int limit);

    void markPublished(long id, OffsetDateTime publishedAt);
}
