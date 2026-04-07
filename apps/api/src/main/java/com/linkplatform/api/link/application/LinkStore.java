package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface LinkStore {

    record DeletedLinkSnapshot(
            String slug,
            String originalUrl,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt,
            String title,
            List<String> tags,
            String hostname,
            long version,
            LinkLifecycleState lifecycleState) {
    }

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

    default boolean transitionLifecycle(
            String slug,
            LinkLifecycleState currentState,
            LinkLifecycleState nextState,
            long expectedVersion,
            long nextVersion,
            long ownerId) {
        return updateLifecycle(slug, currentState, nextState, null, expectedVersion, nextVersion, ownerId);
    }

    default boolean updateLifecycle(
            String slug,
            LinkLifecycleState expectedState,
            LinkLifecycleState nextState,
            OffsetDateTime expiresAt,
            long expectedVersion,
            long nextVersion,
            long ownerId) {
        return transitionLifecycle(slug, expectedState, nextState, expectedVersion, nextVersion, ownerId);
    }

    default boolean restoreDeleted(
            DeletedLinkSnapshot deletedLinkSnapshot,
            LinkLifecycleState restoredState,
            long nextVersion,
            long ownerId) {
        return false;
    }

    default long countActiveLinksByOwner(long ownerId) {
        return 0L;
    }

    default boolean recordClickIfAbsent(LinkClick linkClick) {
        return false;
    }

    default boolean recordActivityIfAbsent(String eventId, LinkActivityEvent linkActivityEvent) {
        return false;
    }

    default Optional<Long> findOwnerIdBySlug(String slug) {
        return Optional.empty();
    }

    default Optional<LinkLifecycleState> findLifecycleStateBySlug(String slug, long ownerId) {
        return Optional.empty();
    }

    default List<Long> findOwnerIdsWithClickHistory() {
        return List.of();
    }

    default List<Long> findOwnerIdsWithClickHistory(Long ownerId, String slug) {
        return findOwnerIdsWithClickHistory();
    }

    default long rebuildClickDailyRollups() {
        return 0L;
    }

    default void projectCatalogEvent(LinkLifecycleEvent linkLifecycleEvent) {
    }

    default void projectDiscoveryEvent(LinkLifecycleEvent linkLifecycleEvent) {
    }

    default void resetCatalogProjection() {
    }

    default void resetCatalogProjection(Long ownerId, String slug) {
        resetCatalogProjection();
    }

    default void resetDiscoveryProjection() {
    }

    default void resetDiscoveryProjection(Long ownerId, String slug) {
        resetDiscoveryProjection();
    }

    default List<LinkClickHistoryRecord> findClickHistoryChunkAfter(long afterId, int limit) {
        return List.of();
    }

    default List<LinkClickHistoryRecord> findClickHistoryChunkAfter(long afterId, int limit, Long ownerId, String slug) {
        return findClickHistoryChunkAfter(afterId, limit);
    }

    default long applyClickHistoryChunkToDailyRollups(List<LinkClickHistoryRecord> clickHistoryChunk) {
        return 0L;
    }

    default void resetClickDailyRollups() {
    }

    default void resetClickDailyRollups(Long ownerId, String slug) {
        resetClickDailyRollups();
    }

    default List<LinkClickHistoryRecord> findClickHistoryChunkForReconciliationAfter(long afterId, int limit) {
        return List.of();
    }

    default List<LinkClickHistoryRecord> findClickHistoryChunkForReconciliationAfter(long afterId, int limit, Long ownerId, String slug) {
        return findClickHistoryChunkForReconciliationAfter(afterId, limit);
    }

    default java.util.Map<String, Long> findDailyRollupTotalsBySlugAndDay(java.util.Set<String> slugDayKeys) {
        return java.util.Map.of();
    }

    default void upsertClickRollupReconciliation(ClickRollupDriftRecord driftRecord) {
    }

    default void repairDailyRollupTotal(String slug, java.time.LocalDate bucketDay, long rawClickCount) {
    }

    default Optional<Link> findBySlug(String slug, OffsetDateTime now) {
        return Optional.empty();
    }

    default Optional<LinkDetails> findDetailsBySlug(String slug, OffsetDateTime now, long ownerId) {
        return Optional.empty();
    }

    default Optional<LinkDetails> findStoredDetailsBySlug(String slug) {
        return Optional.empty();
    }

    default Optional<LinkDetails> findStoredDetailsBySlug(String slug, long ownerId) {
        return Optional.empty();
    }

    default Optional<DeletedLinkSnapshot> findDeletedSnapshotBySlug(String slug, long ownerId) {
        return Optional.empty();
    }

    default List<LinkDetails> findRecent(
            int limit,
            OffsetDateTime now,
            String query,
            LinkLifecycleState state,
            LinkAbuseStatus abuseStatus,
            long ownerId) {
        return List.of();
    }

    default Optional<LinkAbuseStatus> findAbuseStatusBySlug(String slug, long workspaceId) {
        return Optional.empty();
    }

    default boolean flagLinkForAbuse(
            long workspaceId,
            String slug,
            String abuseReason,
            OffsetDateTime flaggedAt,
            Long reviewedByOwnerId,
            String reviewNote,
            boolean preserveQuarantinedState) {
        return false;
    }

    default boolean quarantineLink(
            long workspaceId,
            String slug,
            String abuseReason,
            OffsetDateTime reviewedAt,
            Long reviewedByOwnerId,
            String reviewNote) {
        return false;
    }

    default boolean releaseLink(
            long workspaceId,
            String slug,
            Long reviewedByOwnerId,
            String reviewNote,
            OffsetDateTime reviewedAt) {
        return false;
    }

    default boolean clearFlaggedLink(
            long workspaceId,
            String slug,
            Long reviewedByOwnerId,
            String reviewNote,
            OffsetDateTime reviewedAt) {
        return false;
    }

    default List<LinkSuggestion> findSuggestions(int limit, OffsetDateTime now, String query, long ownerId) {
        return List.of();
    }

    default LinkDiscoveryPage searchDiscovery(OffsetDateTime now, long ownerId, LinkDiscoveryQuery query) {
        return new LinkDiscoveryPage(List.of(), null, false);
    }

    default List<LinkActivityEvent> findRecentActivity(int limit, long ownerId) {
        return List.of();
    }

    default List<LinkActivityEvent> findRecentActivity(
            int limit,
            String tag,
            LinkLifecycleState lifecycle,
            OffsetDateTime asOf,
            long ownerId) {
        return findRecentActivity(limit, ownerId);
    }

    default Optional<LinkTrafficSummaryTotals> findTrafficSummaryTotals(
            String slug,
            OffsetDateTime last24HoursSince,
            java.time.LocalDate last7DaysStartDate,
            long ownerId) {
        return Optional.empty();
    }

    default List<DailyClickBucket> findRecentDailyClickBuckets(String slug, java.time.LocalDate startDate, long ownerId) {
        return List.of();
    }

    default List<DailyClickBucket> findRecentHourlyClickBuckets(String slug, OffsetDateTime since, long ownerId) {
        return List.of();
    }

    default List<TopReferrer> findTopReferrers(String slug, int limit, long ownerId) {
        return List.of();
    }

    default OwnerTrafficTotals findOwnerTrafficTotals(
            OffsetDateTime last1HourSince,
            OffsetDateTime last24HoursSince,
            java.time.LocalDate last7DaysStartDate,
            long ownerId) {
        return new OwnerTrafficTotals(0, 0, 0);
    }

    default List<TopLinkTraffic> findTopLinks(LinkTrafficWindow window, OffsetDateTime now, long ownerId) {
        return List.of();
    }

    default List<TopLinkTraffic> findTopLinks(
            LinkTrafficWindow window,
            OffsetDateTime now,
            int limit,
            String tag,
            LinkLifecycleState lifecycle,
            long ownerId) {
        return findTopLinks(window, now, ownerId);
    }

    default List<TopLinkTraffic> findTopLinks(
            OffsetDateTime from,
            OffsetDateTime to,
            int limit,
            String tag,
            LinkLifecycleState lifecycle,
            OffsetDateTime asOf,
            long ownerId) {
        return List.of();
    }

    default List<TrendingLink> findTrendingLinks(LinkTrafficWindow window, OffsetDateTime now, int limit, long ownerId) {
        return List.of();
    }

    default List<TrendingLink> findTrendingLinks(
            LinkTrafficWindow window,
            OffsetDateTime now,
            int limit,
            String tag,
            LinkLifecycleState lifecycle,
            long ownerId) {
        return findTrendingLinks(window, now, limit, ownerId);
    }

    default List<TrendingLink> findTrendingLinks(
            OffsetDateTime from,
            OffsetDateTime to,
            int limit,
            String tag,
            LinkLifecycleState lifecycle,
            OffsetDateTime asOf,
            long ownerId) {
        return List.of();
    }

    default long countClicksForSlugInRange(String slug, OffsetDateTime from, OffsetDateTime to, long ownerId) {
        return 0L;
    }

    default List<LinkTrafficSeriesBucket> findTrafficSeries(
            String slug,
            OffsetDateTime from,
            OffsetDateTime to,
            String granularity,
            long ownerId) {
        return List.of();
    }

    default Optional<OffsetDateTime> findLatestMaterializedClickAt(long ownerId) {
        return Optional.empty();
    }

    default Optional<OffsetDateTime> findLatestMaterializedClickAt(String slug, long ownerId) {
        return Optional.empty();
    }

    default Optional<OffsetDateTime> findLatestMaterializedActivityAt(long ownerId) {
        return Optional.empty();
    }

    default Optional<OffsetDateTime> findLatestMaterializedActivityAt(String slug, long ownerId) {
        return Optional.empty();
    }
}
