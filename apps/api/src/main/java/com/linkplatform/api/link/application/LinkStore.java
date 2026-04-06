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

    boolean transitionLifecycle(
            String slug,
            LinkLifecycleState currentState,
            LinkLifecycleState nextState,
            long expectedVersion,
            long nextVersion,
            long ownerId);

    long countActiveLinksByOwner(long ownerId);

    boolean recordClickIfAbsent(LinkClick linkClick);

    boolean recordActivityIfAbsent(String eventId, LinkActivityEvent linkActivityEvent);

    Optional<Long> findOwnerIdBySlug(String slug);

    Optional<LinkLifecycleState> findLifecycleStateBySlug(String slug, long ownerId);

    List<Long> findOwnerIdsWithClickHistory();

    long rebuildClickDailyRollups();

    void projectCatalogEvent(LinkLifecycleEvent linkLifecycleEvent);

    void projectDiscoveryEvent(LinkLifecycleEvent linkLifecycleEvent);

    void resetCatalogProjection();

    void resetDiscoveryProjection();

    List<LinkClickHistoryRecord> findClickHistoryChunkAfter(long afterId, int limit);

    long applyClickHistoryChunkToDailyRollups(List<LinkClickHistoryRecord> clickHistoryChunk);

    void resetClickDailyRollups();

    List<LinkClickHistoryRecord> findClickHistoryChunkForReconciliationAfter(long afterId, int limit);

    java.util.Map<String, Long> findDailyRollupTotalsBySlugAndDay(java.util.Set<String> slugDayKeys);

    void upsertClickRollupReconciliation(ClickRollupDriftRecord driftRecord);

    void repairDailyRollupTotal(String slug, java.time.LocalDate bucketDay, long rawClickCount);

    Optional<Link> findBySlug(String slug, OffsetDateTime now);

    Optional<LinkDetails> findDetailsBySlug(String slug, OffsetDateTime now, long ownerId);

    Optional<LinkDetails> findStoredDetailsBySlug(String slug);

    Optional<LinkDetails> findStoredDetailsBySlug(String slug, long ownerId);

    List<LinkDetails> findRecent(int limit, OffsetDateTime now, String query, LinkLifecycleState state, long ownerId);

    List<LinkSuggestion> findSuggestions(int limit, OffsetDateTime now, String query, long ownerId);

    LinkDiscoveryPage searchDiscovery(OffsetDateTime now, long ownerId, LinkDiscoveryQuery query);

    List<LinkActivityEvent> findRecentActivity(int limit, long ownerId);

    Optional<LinkTrafficSummaryTotals> findTrafficSummaryTotals(
            String slug,
            OffsetDateTime last24HoursSince,
            java.time.LocalDate last7DaysStartDate,
            long ownerId);

    List<DailyClickBucket> findRecentDailyClickBuckets(String slug, java.time.LocalDate startDate, long ownerId);

    List<DailyClickBucket> findRecentHourlyClickBuckets(String slug, OffsetDateTime since, long ownerId);

    List<TopReferrer> findTopReferrers(String slug, int limit, long ownerId);

    OwnerTrafficTotals findOwnerTrafficTotals(OffsetDateTime last1HourSince, OffsetDateTime last24HoursSince, java.time.LocalDate last7DaysStartDate, long ownerId);

    List<TopLinkTraffic> findTopLinks(LinkTrafficWindow window, OffsetDateTime now, long ownerId);

    List<TrendingLink> findTrendingLinks(LinkTrafficWindow window, OffsetDateTime now, int limit, long ownerId);
}
