package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface LinkStore {

    boolean save(
            Link link,
            OffsetDateTime expiresAt,
            String title,
            List<String> tags,
            String hostname,
            long version,
            long ownerId);

    boolean update(
            String slug,
            String originalUrl,
            OffsetDateTime expiresAt,
            String title,
            List<String> tags,
            String hostname,
            long expectedVersion,
            long nextVersion,
            long ownerId);

    boolean deleteBySlug(String slug, long expectedVersion, long ownerId);

    long countActiveLinksByOwner(long ownerId);

    boolean recordClickIfAbsent(LinkClick linkClick);

    boolean recordActivityIfAbsent(String eventId, LinkActivityEvent linkActivityEvent);

    long rebuildClickDailyRollups();

    void projectCatalogEvent(LinkLifecycleEvent linkLifecycleEvent);

    void resetCatalogProjection();

    List<LinkClickHistoryRecord> findClickHistoryChunkAfter(long afterId, int limit);

    long applyClickHistoryChunkToDailyRollups(List<LinkClickHistoryRecord> clickHistoryChunk);

    void resetClickDailyRollups();

    Optional<Link> findBySlug(String slug, OffsetDateTime now);

    Optional<LinkDetails> findDetailsBySlug(String slug, OffsetDateTime now, long ownerId);

    Optional<LinkDetails> findStoredDetailsBySlug(String slug);

    Optional<LinkDetails> findStoredDetailsBySlug(String slug, long ownerId);

    List<LinkDetails> findRecent(int limit, OffsetDateTime now, String query, LinkLifecycleState state, long ownerId);

    List<LinkSuggestion> findSuggestions(int limit, OffsetDateTime now, String query, long ownerId);

    List<LinkActivityEvent> findRecentActivity(int limit);

    Optional<LinkTrafficSummaryTotals> findTrafficSummaryTotals(
            String slug,
            OffsetDateTime last24HoursSince,
            java.time.LocalDate last7DaysStartDate);

    List<DailyClickBucket> findRecentDailyClickBuckets(String slug, java.time.LocalDate startDate);

    List<TopLinkTraffic> findTopLinks(LinkTrafficWindow window, OffsetDateTime now);

    List<TrendingLink> findTrendingLinks(LinkTrafficWindow window, OffsetDateTime now, int limit);
}
