package com.linkplatform.api.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.linkplatform.api.link.application.ClickRollupDriftRecord;
import com.linkplatform.api.link.application.DailyClickBucket;
import com.linkplatform.api.link.application.LinkActivityEvent;
import com.linkplatform.api.link.application.LinkClick;
import com.linkplatform.api.link.application.LinkClickHistoryRecord;
import com.linkplatform.api.link.application.LinkDetails;
import com.linkplatform.api.link.application.LinkDiscoveryPage;
import com.linkplatform.api.link.application.LinkDiscoveryQuery;
import com.linkplatform.api.link.application.LinkAbuseStatus;
import com.linkplatform.api.link.application.LinkLifecycleEvent;
import com.linkplatform.api.link.application.LinkLifecycleState;
import com.linkplatform.api.link.application.LinkReadCache;
import com.linkplatform.api.link.application.LinkStore;
import com.linkplatform.api.link.application.LinkSuggestion;
import com.linkplatform.api.link.application.LinkTrafficSummary;
import com.linkplatform.api.link.application.LinkTrafficSummaryTotals;
import com.linkplatform.api.link.application.LinkTrafficWindow;
import com.linkplatform.api.link.application.OwnerTrafficTotals;
import com.linkplatform.api.link.application.TopLinkTraffic;
import com.linkplatform.api.link.application.TopReferrer;
import com.linkplatform.api.link.application.TrendingLink;
import com.linkplatform.api.link.application.LinkLifecycleHistoryRecord;
import com.linkplatform.api.link.application.LinkLifecycleOutboxStore;
import com.linkplatform.api.owner.application.SecurityEventStore;
import com.linkplatform.api.owner.application.SecurityEventType;
import com.linkplatform.api.link.domain.Link;
import com.linkplatform.api.link.domain.LinkSlug;
import com.linkplatform.api.link.domain.OriginalUrl;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class ProjectionJobServiceReconciliationTest {

    @Test
    void reconciliationRepairsDriftAndRecordsSecurityEvents() {
        TestLinkStore linkStore = new TestLinkStore();
        linkStore.ownerIdBySlug.put("docs", 1L);
        linkStore.rollups.put("docs|2026-04-06", 1L);
        linkStore.history = List.of(
                new LinkClickHistoryRecord(1L, "docs", LocalDate.parse("2026-04-06")),
                new LinkClickHistoryRecord(2L, "docs", LocalDate.parse("2026-04-06")),
                new LinkClickHistoryRecord(3L, "docs", LocalDate.parse("2026-04-06")));
        TestSecurityEventStore securityEventStore = new TestSecurityEventStore();
        ProjectionJobService service = new ProjectionJobService(
                new NoOpProjectionJobStore(),
                new NoOpLifecycleOutboxStore(),
                linkStore,
                new TestLinkReadCache(),
                securityEventStore,
                100);

        ProjectionJobChunkResult result = service.executeClaimedJobChunk(new ProjectionJob(
                1L,
                ProjectionJobType.CLICK_ROLLUP_RECONCILE,
                ProjectionJobStatus.RUNNING,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null,
                0L,
                0L,
                null,
                null,
                null));

        assertThat(result.completed()).isTrue();
        assertThat(result.driftCount()).isEqualTo(1L);
        assertThat(result.repairCount()).isEqualTo(1L);
        assertThat(linkStore.rollups.get("docs|2026-04-06")).isEqualTo(3L);
        assertThat(linkStore.reconciliations).hasSize(1);
        assertThat(securityEventStore.types).containsExactly(
                SecurityEventType.CLICK_ROLLUP_DRIFT_DETECTED,
                SecurityEventType.CLICK_ROLLUP_REPAIRED);
    }

    private static final class TestLinkStore implements LinkStore {
        private final Map<String, Long> ownerIdBySlug = new ConcurrentHashMap<>();
        private final Map<String, Long> rollups = new ConcurrentHashMap<>();
        private final List<ClickRollupDriftRecord> reconciliations = new ArrayList<>();
        private List<LinkClickHistoryRecord> history = List.of();

        @Override public boolean save(Link link, OffsetDateTime expiresAt, String title, List<String> tags, String hostname, long version, long ownerId) { return false; }
        @Override public boolean update(String slug, String originalUrl, OffsetDateTime expiresAt, String title, List<String> tags, String hostname, long expectedVersion, long nextVersion, long ownerId) { return false; }
        @Override public boolean deleteBySlug(String slug, long expectedVersion, long ownerId) { return false; }
        @Override public boolean transitionLifecycle(String slug, LinkLifecycleState currentState, LinkLifecycleState nextState, long expectedVersion, long nextVersion, long ownerId) { return false; }
        @Override public long countActiveLinksByOwner(long ownerId) { return 0; }
        @Override public boolean recordClickIfAbsent(LinkClick linkClick) { return false; }
        @Override public boolean recordActivityIfAbsent(String eventId, LinkActivityEvent linkActivityEvent) { return false; }
        @Override public Optional<Long> findOwnerIdBySlug(String slug) { return Optional.ofNullable(ownerIdBySlug.get(slug)); }
        @Override public Optional<LinkLifecycleState> findLifecycleStateBySlug(String slug, long ownerId) { return Optional.of(LinkLifecycleState.ACTIVE); }
        @Override public List<Long> findOwnerIdsWithClickHistory() { return List.of(1L); }
        @Override public long rebuildClickDailyRollups() { return 0; }
        @Override public void projectCatalogEvent(LinkLifecycleEvent linkLifecycleEvent) { }
        @Override public void projectDiscoveryEvent(LinkLifecycleEvent linkLifecycleEvent) { }
        @Override public void resetCatalogProjection() { }
        @Override public void resetDiscoveryProjection() { }
        @Override public List<LinkClickHistoryRecord> findClickHistoryChunkAfter(long afterId, int limit) { return List.of(); }
        @Override public long applyClickHistoryChunkToDailyRollups(List<LinkClickHistoryRecord> clickHistoryChunk) { return 0; }
        @Override public void resetClickDailyRollups() { }
        @Override public List<LinkClickHistoryRecord> findClickHistoryChunkForReconciliationAfter(long afterId, int limit) { return history; }
        @Override public Map<String, Long> findDailyRollupTotalsBySlugAndDay(Set<String> slugDayKeys) { return rollups; }
        @Override public void upsertClickRollupReconciliation(ClickRollupDriftRecord driftRecord) { reconciliations.add(driftRecord); }
        @Override public void repairDailyRollupTotal(String slug, LocalDate bucketDay, long rawClickCount) { rollups.put(slug + "|" + bucketDay, rawClickCount); }
        @Override public Optional<Link> findBySlug(String slug, OffsetDateTime now) { return Optional.of(new Link(new LinkSlug(slug), new OriginalUrl("https://example.com"))); }
        @Override public Optional<LinkDetails> findDetailsBySlug(String slug, OffsetDateTime now, long ownerId) { return Optional.empty(); }
        @Override public Optional<LinkDetails> findStoredDetailsBySlug(String slug) { return Optional.empty(); }
        @Override public Optional<LinkDetails> findStoredDetailsBySlug(String slug, long ownerId) { return Optional.empty(); }
        @Override public List<LinkDetails> findRecent(int limit, OffsetDateTime now, String query, LinkLifecycleState state, LinkAbuseStatus abuseStatus, long ownerId) { return List.of(); }
        @Override public List<LinkSuggestion> findSuggestions(int limit, OffsetDateTime now, String query, long ownerId) { return List.of(); }
        @Override public LinkDiscoveryPage searchDiscovery(OffsetDateTime now, long ownerId, LinkDiscoveryQuery query) { return new LinkDiscoveryPage(List.of(), null, false); }
        @Override public List<LinkActivityEvent> findRecentActivity(int limit, long ownerId) { return List.of(); }
        @Override public Optional<LinkTrafficSummaryTotals> findTrafficSummaryTotals(String slug, OffsetDateTime last24HoursSince, LocalDate last7DaysStartDate, long ownerId) { return Optional.empty(); }
        @Override public List<DailyClickBucket> findRecentDailyClickBuckets(String slug, LocalDate startDate, long ownerId) { return List.of(); }
        @Override public List<DailyClickBucket> findRecentHourlyClickBuckets(String slug, OffsetDateTime since, long ownerId) { return List.of(); }
        @Override public List<TopReferrer> findTopReferrers(String slug, int limit, long ownerId) { return List.of(); }
        @Override public OwnerTrafficTotals findOwnerTrafficTotals(OffsetDateTime last1HourSince, OffsetDateTime last24HoursSince, LocalDate last7DaysStartDate, long ownerId) { return new OwnerTrafficTotals(0, 0, 0); }
        @Override public List<TopLinkTraffic> findTopLinks(LinkTrafficWindow window, OffsetDateTime now, long ownerId) { return List.of(); }
        @Override public List<TrendingLink> findTrendingLinks(LinkTrafficWindow window, OffsetDateTime now, int limit, long ownerId) { return List.of(); }
    }

    private static final class TestSecurityEventStore implements SecurityEventStore {
        private final List<SecurityEventType> types = new ArrayList<>();
        @Override public void record(SecurityEventType eventType, Long ownerId, String apiKeyHash, String requestMethod, String requestPath, String remoteAddress, String detailSummary, OffsetDateTime occurredAt) { types.add(eventType); }
    }

    private static final class TestLinkReadCache implements LinkReadCache {
        @Override public long getPublicRedirectGeneration(String slug) { return 0; }
        @Override public Optional<Link> getPublicRedirect(String slug, long generation) { return Optional.empty(); }
        @Override public void putPublicRedirect(String slug, long generation, Link link) { }
        @Override public void invalidatePublicRedirect(String slug) { }
        @Override public long getOwnerControlPlaneGeneration(long ownerId) { return 0; }
        @Override public Optional<LinkDetails> getOwnerLinkDetails(long ownerId, long generation, String slug) { return Optional.empty(); }
        @Override public void putOwnerLinkDetails(long ownerId, long generation, String slug, LinkDetails linkDetails) { }
        @Override public Optional<List<LinkDetails>> getOwnerRecentLinks(long ownerId, long generation, int limit, String query, LinkLifecycleState state) { return Optional.empty(); }
        @Override public void putOwnerRecentLinks(long ownerId, long generation, int limit, String query, LinkLifecycleState state, List<LinkDetails> linkDetails) { }
        @Override public Optional<List<LinkSuggestion>> getOwnerSuggestions(long ownerId, long generation, String query, int limit) { return Optional.empty(); }
        @Override public void putOwnerSuggestions(long ownerId, long generation, String query, int limit, List<LinkSuggestion> suggestions) { }
        @Override public Optional<LinkDiscoveryPage> getOwnerDiscoveryPage(long ownerId, long generation, LinkDiscoveryQuery query) { return Optional.empty(); }
        @Override public void putOwnerDiscoveryPage(long ownerId, long generation, LinkDiscoveryQuery query, LinkDiscoveryPage page) { }
        @Override public long getOwnerAnalyticsGeneration(long ownerId) { return 0; }
        @Override public Optional<List<LinkActivityEvent>> getOwnerRecentActivity(long ownerId, long generation, int limit) { return Optional.empty(); }
        @Override public void putOwnerRecentActivity(long ownerId, long generation, int limit, List<LinkActivityEvent> activityEvents) { }
        @Override public Optional<LinkTrafficSummary> getOwnerTrafficSummary(long ownerId, long generation, String slug) { return Optional.empty(); }
        @Override public void putOwnerTrafficSummary(long ownerId, long generation, String slug, LinkTrafficSummary summary) { }
        @Override public Optional<List<TopLinkTraffic>> getOwnerTopLinks(long ownerId, long generation, LinkTrafficWindow window) { return Optional.empty(); }
        @Override public void putOwnerTopLinks(long ownerId, long generation, LinkTrafficWindow window, List<TopLinkTraffic> topLinks) { }
        @Override public Optional<List<TrendingLink>> getOwnerTrendingLinks(long ownerId, long generation, LinkTrafficWindow window, int limit) { return Optional.empty(); }
        @Override public void putOwnerTrendingLinks(long ownerId, long generation, LinkTrafficWindow window, int limit, List<TrendingLink> trendingLinks) { }
        @Override public void invalidateOwnerControlPlane(long ownerId) { }
        @Override public void invalidateOwnerAnalytics(long ownerId) { }
    }

    private static final class NoOpProjectionJobStore implements ProjectionJobStore {
        @Override public ProjectionJob createJob(ProjectionJobType jobType, OffsetDateTime requestedAt) { return null; }
        @Override public ProjectionJob createJob(ProjectionJobType jobType, OffsetDateTime requestedAt, Long ownerId, String slug) { return null; }
        @Override public ProjectionJob createJob(ProjectionJobType jobType, OffsetDateTime requestedAt, Long ownerId, Long workspaceId, String slug, OffsetDateTime rangeStart, OffsetDateTime rangeEnd, Long requestedByOwnerId, String operatorNote) { return null; }
        @Override public Optional<ProjectionJob> findById(long id) { return Optional.empty(); }
        @Override public Optional<ProjectionJob> findByIdVisibleToWorkspace(long id, long workspaceId, long ownerId, boolean personalWorkspace) { return Optional.empty(); }
        @Override public List<ProjectionJob> findRecent(int limit) { return List.of(); }
        @Override public List<ProjectionJob> findRecentVisibleToWorkspace(int limit, long workspaceId, long ownerId, boolean personalWorkspace) { return List.of(); }
        @Override public Optional<ProjectionJob> claimNext(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil) { return Optional.empty(); }
        @Override public Optional<ProjectionJob> claimNextQueued(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil) { return Optional.empty(); }
        @Override public void markProgress(long id, OffsetDateTime occurredAt, long processedCount, Long checkpointId) { }
        @Override public void markProgress(long id, long processedCount, Long checkpointId) { }
        @Override public void markCompleted(long id, OffsetDateTime completedAt, long processedCount, Long checkpointId) { }
        @Override public void markFailed(long id, OffsetDateTime failedAt, long failedItemsIncrement, String errorSummary) { }
        @Override public void markFailed(long id, OffsetDateTime failedAt, String errorSummary) { }
        @Override public long countQueued() { return 0; }
        @Override public long countActive() { return 0; }
        @Override public long countQueued(Long workspaceId) { return 0; }
        @Override public long countActive(Long workspaceId) { return 0; }
        @Override public long countFailed(Long workspaceId) { return 0; }
        @Override public long countCompleted(Long workspaceId) { return 0; }
        @Override public Optional<OffsetDateTime> findLatestStartedAt(Long workspaceId) { return Optional.empty(); }
        @Override public Optional<OffsetDateTime> findLatestFailedAt(Long workspaceId) { return Optional.empty(); }
    }

    private static final class NoOpLifecycleOutboxStore implements LinkLifecycleOutboxStore {
        @Override public void saveLinkLifecycleEvent(LinkLifecycleEvent linkLifecycleEvent) { }
        @Override public long countUnpublished() { return 0; }
        @Override public long countEligible(OffsetDateTime now) { return 0; }
        @Override public long countParked() { return 0; }
        @Override public Double findOldestEligibleAgeSeconds(OffsetDateTime now) { return 0d; }
        @Override public List<com.linkplatform.api.link.application.LinkLifecycleOutboxRecord> claimBatch(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil, int limit) { return List.of(); }
        @Override public void markPublished(long id, OffsetDateTime publishedAt) { }
        @Override public void recordPublishFailure(long id, int attemptCount, OffsetDateTime nextAttemptAt, String lastErrorSummary, OffsetDateTime parkedAt) { }
        @Override public List<com.linkplatform.api.link.application.LinkLifecycleOutboxRecord> findParked(int limit) { return List.of(); }
        @Override public List<LinkLifecycleEvent> findAllHistory() { return List.of(); }
        @Override public List<LinkLifecycleHistoryRecord> findHistoryChunkAfter(long afterId, int limit) { return List.of(); }
        @Override public boolean requeueParked(long id, OffsetDateTime nextAttemptAt) { return false; }
    }
}
