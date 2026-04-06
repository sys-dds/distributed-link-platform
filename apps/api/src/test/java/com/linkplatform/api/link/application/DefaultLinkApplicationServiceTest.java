package com.linkplatform.api.link.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linkplatform.api.link.domain.Link;
import com.linkplatform.api.link.domain.LinkSlug;
import com.linkplatform.api.link.domain.OriginalUrl;
import com.linkplatform.api.owner.application.AuthenticatedOwner;
import com.linkplatform.api.owner.application.OwnerPlan;
import com.linkplatform.api.owner.application.OwnerStore;
import com.linkplatform.api.owner.application.SecurityEventStore;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class DefaultLinkApplicationServiceTest {

    private static final AuthenticatedOwner FREE_OWNER =
            new AuthenticatedOwner(1L, "free-owner", "Free Owner", OwnerPlan.FREE);

    private final TestLinkStore linkStore = new TestLinkStore();
    private final TestLinkReadCache linkReadCache = new TestLinkReadCache();
    private final DefaultLinkApplicationService service = new DefaultLinkApplicationService(
            linkStore,
            new TestAnalyticsOutboxStore(),
            new TestLinkLifecycleOutboxStore(),
            new TestLinkMutationIdempotencyStore(),
            new TestOwnerStore(),
            new TestSecurityEventStore(),
            linkReadCache,
            "http://localhost:80");

    @Test
    void analyticsSummaryViewAddsWindowComparisonAndFreshness() {
        OffsetDateTime start = OffsetDateTime.parse("2026-04-06T00:00:00Z");
        OffsetDateTime end = OffsetDateTime.parse("2026-04-06T03:00:00Z");
        linkStore.seedLink("metrics-link", FREE_OWNER.id());
        linkStore.summaryBySlug.put("metrics-link", new LinkTrafficSummaryTotals("metrics-link", "https://example.com/metrics", 8L, 3L, 8L));
        linkStore.dailyBucketsBySlug.put("metrics-link", List.of(new DailyClickBucket(LocalDate.parse("2026-04-01"), 8L)));
        linkStore.rangeClicks.put(key("metrics-link", start, end), 3L);
        linkStore.rangeClicks.put(key("metrics-link", start.minusHours(3), start), 1L);
        linkStore.latestClickAtBySlug.put("metrics-link", end.minusMinutes(5));
        linkStore.latestActivityAtBySlug.put("metrics-link", end.minusMinutes(2));

        LinkApplicationService.AnalyticsSummaryView summaryView =
                service.getTrafficSummary(FREE_OWNER, "metrics-link", AnalyticsRange.required(start, end, true));

        assertEquals(3L, summaryView.windowClicks());
        assertEquals(1L, summaryView.comparison().previousWindowClicks());
        assertEquals(2L, summaryView.comparison().clickChangeAbsolute());
        assertEquals(200D, summaryView.comparison().clickChangePercent());
        assertNotNull(summaryView.freshness().asOf());
        assertEquals(end.minusMinutes(5), summaryView.freshness().latestMaterializedClickAt());
        assertEquals(end.minusMinutes(2), summaryView.freshness().latestMaterializedActivityAt());
    }

    @Test
    void trafficSeriesZeroFillsMissingBuckets() {
        OffsetDateTime start = OffsetDateTime.parse("2026-04-06T00:00:00Z");
        OffsetDateTime end = OffsetDateTime.parse("2026-04-06T04:00:00Z");
        linkStore.seedLink("series-link", FREE_OWNER.id());
        linkStore.summaryBySlug.put("series-link", new LinkTrafficSummaryTotals("series-link", "https://example.com/series", 4L, 4L, 4L));
        linkStore.seriesBySlug.put("series-link", List.of(
                new LinkTrafficSeriesBucket(start, start.plusHours(1), 1L),
                new LinkTrafficSeriesBucket(start.plusHours(2), start.plusHours(3), 2L)));
        linkStore.rangeClicks.put(key("series-link", start.minusHours(4), start), 1L);

        LinkApplicationService.LinkTrafficSeriesView seriesView =
                service.getTrafficSeries(FREE_OWNER, "series-link", AnalyticsRange.required(start, end, true), "hour");

        assertEquals(4, seriesView.buckets().size());
        assertEquals(1L, seriesView.buckets().get(0).clickTotal());
        assertEquals(0L, seriesView.buckets().get(1).clickTotal());
        assertEquals(2L, seriesView.buckets().get(2).clickTotal());
        assertEquals(0L, seriesView.buckets().get(3).clickTotal());
        assertEquals(3L, seriesView.comparison().currentWindowClicks());
        assertEquals(1L, seriesView.comparison().previousWindowClicks());
    }

    @Test
    void filteredTopLinksUseTagLifecycleAndOwnerScopedStorePath() {
        OffsetDateTime now = OffsetDateTime.parse("2026-04-06T09:00:00Z");
        linkStore.fixedNowClick = now.minusMinutes(1);
        linkStore.filteredTopLinks = List.of(
                new TopLinkTraffic("match-a", "https://example.com/match-a", 5L),
                new TopLinkTraffic("match-b", "https://example.com/match-b", 3L));

        List<TopLinkTraffic> topLinks = service.getTopLinks(
                FREE_OWNER,
                LinkTrafficWindow.LAST_24_HOURS,
                AnalyticsRange.required(now.minusHours(6), now, false),
                "team",
                "active",
                2);

        assertEquals(List.of("match-a", "match-b"), topLinks.stream().map(TopLinkTraffic::slug).toList());
        assertEquals("team", linkStore.lastTagFilter);
        assertEquals(LinkLifecycleState.ACTIVE, linkStore.lastLifecycleFilter);
        assertEquals(FREE_OWNER.id(), linkStore.lastOwnerId);
    }

    @Test
    void filteredTrendingUsesPreviousEqualWindowAndRealRanking() {
        OffsetDateTime now = OffsetDateTime.parse("2026-04-06T09:00:00Z");
        linkStore.filteredTrendingLinks = List.of(
                new TrendingLink("winner", "https://example.com/winner", 4L, 5L, 1L),
                new TrendingLink("runner-up", "https://example.com/runner", 2L, 3L, 1L));

        List<TrendingLink> trendingLinks = service.getTrendingLinks(
                FREE_OWNER,
                LinkTrafficWindow.LAST_24_HOURS,
                AnalyticsRange.required(now.minusHours(6), now, false),
                "team",
                "active",
                2);

        assertEquals("winner", trendingLinks.getFirst().slug());
        assertEquals(5L, trendingLinks.getFirst().currentWindowClicks());
        assertEquals(1L, trendingLinks.getFirst().previousWindowClicks());
    }

    @Test
    void filteredActivityRespectsTagLifecycleAndLimit() {
        linkStore.filteredActivity = List.of(
                new LinkActivityEvent(
                        FREE_OWNER.id(),
                        LinkActivityType.UPDATED,
                        "activity-link",
                        "https://example.com/activity",
                        "Activity",
                        List.of("team"),
                        "example.com",
                        null,
                        OffsetDateTime.parse("2026-04-06T08:59:00Z")));

        List<LinkActivityEvent> activity = service.getRecentActivity(FREE_OWNER, 1, "team", "active");

        assertEquals(1, activity.size());
        assertEquals("activity-link", activity.getFirst().slug());
        assertEquals("team", linkStore.lastTagFilter);
        assertEquals(LinkLifecycleState.ACTIVE, linkStore.lastLifecycleFilter);
    }

    @Test
    void analyticsCachesRefreshAfterOwnerAnalyticsInvalidation() {
        linkStore.seedLink("cache-link", FREE_OWNER.id());
        linkStore.summaryBySlug.put("cache-link", new LinkTrafficSummaryTotals("cache-link", "https://example.com/cache", 1L, 1L, 1L));
        linkStore.dailyBucketsBySlug.put("cache-link", List.of(new DailyClickBucket(LocalDate.parse("2026-04-01"), 1L)));
        linkStore.topLinksByOwner.put(FREE_OWNER.id(), List.of(new TopLinkTraffic("cache-link", "https://example.com/cache", 1L)));
        linkStore.trendingLinksByOwner.put(FREE_OWNER.id(), List.of(new TrendingLink("cache-link", "https://example.com/cache", 1L, 1L, 0L)));

        assertEquals(1L, service.getTrafficSummary(FREE_OWNER, "cache-link").totalClicks());
        assertEquals(1L, service.getTopLinks(FREE_OWNER, LinkTrafficWindow.LAST_24_HOURS).getFirst().clickTotal());

        linkStore.summaryBySlug.put("cache-link", new LinkTrafficSummaryTotals("cache-link", "https://example.com/cache", 3L, 3L, 3L));
        linkStore.dailyBucketsBySlug.put("cache-link", List.of(new DailyClickBucket(LocalDate.parse("2026-04-01"), 3L)));
        linkStore.topLinksByOwner.put(FREE_OWNER.id(), List.of(new TopLinkTraffic("cache-link", "https://example.com/cache", 3L)));

        assertEquals(1L, service.getTrafficSummary(FREE_OWNER, "cache-link").totalClicks());
        linkReadCache.invalidateOwnerAnalytics(FREE_OWNER.id());
        assertEquals(3L, service.getTrafficSummary(FREE_OWNER, "cache-link").totalClicks());
        assertEquals(3L, service.getTopLinks(FREE_OWNER, LinkTrafficWindow.LAST_24_HOURS).getFirst().clickTotal());
    }

    @Test
    void invalidLifecycleFilterIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.getTopLinks(FREE_OWNER, LinkTrafficWindow.LAST_24_HOURS, null, null, "broken", 10));
    }

    private static String key(String slug, OffsetDateTime from, OffsetDateTime to) {
        return slug + "|" + from + "|" + to;
    }

    private static final class TestLinkStore implements LinkStore {
        private final Map<String, Link> linksBySlug = new ConcurrentHashMap<>();
        private final Map<String, Long> ownerBySlug = new ConcurrentHashMap<>();
        private final Map<String, LinkTrafficSummaryTotals> summaryBySlug = new ConcurrentHashMap<>();
        private final Map<String, List<DailyClickBucket>> dailyBucketsBySlug = new ConcurrentHashMap<>();
        private final Map<String, Long> rangeClicks = new ConcurrentHashMap<>();
        private final Map<String, List<LinkTrafficSeriesBucket>> seriesBySlug = new ConcurrentHashMap<>();
        private final Map<Long, List<TopLinkTraffic>> topLinksByOwner = new ConcurrentHashMap<>();
        private final Map<Long, List<TrendingLink>> trendingLinksByOwner = new ConcurrentHashMap<>();
        private final Map<String, OffsetDateTime> latestClickAtBySlug = new ConcurrentHashMap<>();
        private final Map<String, OffsetDateTime> latestActivityAtBySlug = new ConcurrentHashMap<>();
        private List<TopLinkTraffic> filteredTopLinks = List.of();
        private List<TrendingLink> filteredTrendingLinks = List.of();
        private List<LinkActivityEvent> filteredActivity = List.of();
        private String lastTagFilter;
        private LinkLifecycleState lastLifecycleFilter;
        private long lastOwnerId;
        private OffsetDateTime fixedNowClick;

        void seedLink(String slug, long ownerId) {
            linksBySlug.put(slug, new Link(new LinkSlug(slug), new OriginalUrl("https://example.com/" + slug)));
            ownerBySlug.put(slug, ownerId);
        }

        @Override
        public boolean save(
                Link link,
                OffsetDateTime expiresAt,
                String title,
                List<String> tags,
                String hostname,
                long version,
                long ownerId) {
            seedLink(link.slug().value(), ownerId);
            return true;
        }

        @Override
        public boolean update(
                String slug,
                String originalUrl,
                OffsetDateTime expiresAt,
                String title,
                List<String> tags,
                String hostname,
                long expectedVersion,
                long nextVersion,
                long ownerId) {
            linksBySlug.put(slug, new Link(new LinkSlug(slug), new OriginalUrl(originalUrl)));
            ownerBySlug.put(slug, ownerId);
            return true;
        }

        @Override
        public boolean deleteBySlug(String slug, long expectedVersion, long ownerId) {
            linksBySlug.remove(slug);
            ownerBySlug.remove(slug);
            return true;
        }

        @Override
        public Optional<LinkTrafficSummaryTotals> findTrafficSummaryTotals(
                String slug,
                OffsetDateTime last24HoursSince,
                LocalDate last7DaysStartDate,
                long ownerId) {
            if (!ownerBySlug.containsKey(slug) || ownerBySlug.get(slug) != ownerId) {
                return Optional.empty();
            }
            return Optional.ofNullable(summaryBySlug.get(slug));
        }

        @Override
        public List<DailyClickBucket> findRecentDailyClickBuckets(String slug, LocalDate startDate, long ownerId) {
            return dailyBucketsBySlug.getOrDefault(slug, List.of());
        }

        @Override
        public long countClicksForSlugInRange(String slug, OffsetDateTime from, OffsetDateTime to, long ownerId) {
            return rangeClicks.getOrDefault(key(slug, from, to), 0L);
        }

        @Override
        public List<LinkTrafficSeriesBucket> findTrafficSeries(
                String slug,
                OffsetDateTime from,
                OffsetDateTime to,
                String granularity,
                long ownerId) {
            return seriesBySlug.getOrDefault(slug, List.of());
        }

        @Override
        public List<TopLinkTraffic> findTopLinks(LinkTrafficWindow window, OffsetDateTime now, long ownerId) {
            return topLinksByOwner.getOrDefault(ownerId, List.of());
        }

        @Override
        public List<TopLinkTraffic> findTopLinks(
                OffsetDateTime from,
                OffsetDateTime to,
                int limit,
                String tag,
                LinkLifecycleState lifecycle,
                OffsetDateTime asOf,
                long ownerId) {
            lastTagFilter = tag;
            lastLifecycleFilter = lifecycle;
            lastOwnerId = ownerId;
            return filteredTopLinks;
        }

        @Override
        public List<TrendingLink> findTrendingLinks(LinkTrafficWindow window, OffsetDateTime now, int limit, long ownerId) {
            return trendingLinksByOwner.getOrDefault(ownerId, List.of());
        }

        @Override
        public List<TrendingLink> findTrendingLinks(
                OffsetDateTime from,
                OffsetDateTime to,
                int limit,
                String tag,
                LinkLifecycleState lifecycle,
                OffsetDateTime asOf,
                long ownerId) {
            lastTagFilter = tag;
            lastLifecycleFilter = lifecycle;
            lastOwnerId = ownerId;
            return filteredTrendingLinks;
        }

        @Override
        public List<LinkActivityEvent> findRecentActivity(
                int limit,
                String tag,
                LinkLifecycleState lifecycle,
                OffsetDateTime asOf,
                long ownerId) {
            lastTagFilter = tag;
            lastLifecycleFilter = lifecycle;
            lastOwnerId = ownerId;
            return filteredActivity.stream().limit(limit).toList();
        }

        @Override
        public Optional<OffsetDateTime> findLatestMaterializedClickAt(long ownerId) {
            return latestClickAtBySlug.values().stream().max(OffsetDateTime::compareTo);
        }

        @Override
        public Optional<OffsetDateTime> findLatestMaterializedClickAt(String slug, long ownerId) {
            return Optional.ofNullable(latestClickAtBySlug.get(slug));
        }

        @Override
        public Optional<OffsetDateTime> findLatestMaterializedActivityAt(long ownerId) {
            return latestActivityAtBySlug.values().stream().max(OffsetDateTime::compareTo);
        }

        @Override
        public Optional<OffsetDateTime> findLatestMaterializedActivityAt(String slug, long ownerId) {
            return Optional.ofNullable(latestActivityAtBySlug.get(slug));
        }

        @Override
        public Optional<Link> findBySlug(String slug, OffsetDateTime now) {
            return Optional.ofNullable(linksBySlug.get(slug));
        }
    }

    private static final class TestLinkMutationIdempotencyStore implements LinkMutationIdempotencyStore {
        @Override
        public Optional<LinkMutationIdempotencyRecord> findByKey(long ownerId, String idempotencyKey) {
            return Optional.empty();
        }

        @Override
        public void saveResult(
                long ownerId,
                String idempotencyKey,
                String operation,
                String requestHash,
                LinkMutationResult result,
                OffsetDateTime createdAt) {
        }
    }

    private static final class TestAnalyticsOutboxStore implements AnalyticsOutboxStore {
        @Override public void saveRedirectClickEvent(RedirectClickAnalyticsEvent redirectClickAnalyticsEvent) { }
        @Override public long countUnpublished() { return 0; }
        @Override public long countEligible(OffsetDateTime now) { return 0; }
        @Override public long countParked() { return 0; }
        @Override public Double findOldestEligibleAgeSeconds(OffsetDateTime now) { return null; }
        @Override public List<AnalyticsOutboxRecord> claimBatch(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil, int limit) { return List.of(); }
        @Override public void markPublished(long id, OffsetDateTime publishedAt) { }
        @Override public void recordPublishFailure(long id, int attemptCount, OffsetDateTime nextAttemptAt, String lastErrorSummary, OffsetDateTime parkedAt) { }
        @Override public List<AnalyticsOutboxRecord> findParked(int limit) { return List.of(); }
        @Override public boolean requeueParked(long id, OffsetDateTime nextAttemptAt) { return false; }
        @Override public long archivePublishedBefore(OffsetDateTime cutoff, int limit) { return 0; }
        @Override public long countArchived() { return 0; }
    }

    private static final class TestLinkLifecycleOutboxStore implements LinkLifecycleOutboxStore {
        @Override public void saveLinkLifecycleEvent(LinkLifecycleEvent linkLifecycleEvent) { }
        @Override public long countUnpublished() { return 0; }
        @Override public long countEligible(OffsetDateTime now) { return 0; }
        @Override public long countParked() { return 0; }
        @Override public Double findOldestEligibleAgeSeconds(OffsetDateTime now) { return null; }
        @Override public List<LinkLifecycleOutboxRecord> claimBatch(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil, int limit) { return List.of(); }
        @Override public void markPublished(long id, OffsetDateTime publishedAt) { }
        @Override public void recordPublishFailure(long id, int attemptCount, OffsetDateTime nextAttemptAt, String lastErrorSummary, OffsetDateTime parkedAt) { }
        @Override public List<LinkLifecycleEvent> findAllHistory() { return List.of(); }
        @Override public List<LinkLifecycleHistoryRecord> findHistoryChunkAfter(long afterId, int limit) { return List.of(); }
        @Override public List<LinkLifecycleOutboxRecord> findParked(int limit) { return List.of(); }
        @Override public boolean requeueParked(long id, OffsetDateTime nextAttemptAt) { return false; }
    }

    private static final class TestOwnerStore implements OwnerStore {
        @Override
        public Optional<AuthenticatedOwner> findByApiKeyHash(String apiKeyHash) {
            return Optional.empty();
        }

        @Override
        public void lockById(long ownerId) {
        }
    }

    private static final class TestSecurityEventStore implements SecurityEventStore {
        @Override
        public void record(
                com.linkplatform.api.owner.application.SecurityEventType eventType,
                Long ownerId,
                String apiKeyHash,
                String requestMethod,
                String requestPath,
                String remoteAddress,
                String detailSummary,
                OffsetDateTime occurredAt) {
        }
    }

    private static final class TestLinkReadCache implements LinkReadCache {
        private final Map<Long, Long> analyticsGenerationByOwner = new HashMap<>();
        private final Map<String, LinkTrafficSummary> trafficSummaryCache = new HashMap<>();
        private final Map<String, List<TopLinkTraffic>> topLinksCache = new HashMap<>();
        private final Map<String, List<TrendingLink>> trendingLinksCache = new HashMap<>();

        @Override
        public long getPublicRedirectGeneration(String slug) {
            return 0;
        }

        @Override
        public Optional<Link> getPublicRedirect(String slug, long generation) {
            return Optional.empty();
        }

        @Override
        public void putPublicRedirect(String slug, long generation, Link link) {
        }

        @Override
        public void invalidatePublicRedirect(String slug) {
        }

        @Override
        public long getOwnerControlPlaneGeneration(long ownerId) {
            return 0;
        }

        @Override
        public Optional<LinkDetails> getOwnerLinkDetails(long ownerId, long generation, String slug) {
            return Optional.empty();
        }

        @Override
        public void putOwnerLinkDetails(long ownerId, long generation, String slug, LinkDetails linkDetails) {
        }

        @Override
        public Optional<List<LinkDetails>> getOwnerRecentLinks(long ownerId, long generation, int limit, String query, LinkLifecycleState state) {
            return Optional.empty();
        }

        @Override
        public void putOwnerRecentLinks(long ownerId, long generation, int limit, String query, LinkLifecycleState state, List<LinkDetails> linkDetails) {
        }

        @Override
        public Optional<List<LinkSuggestion>> getOwnerSuggestions(long ownerId, long generation, String query, int limit) {
            return Optional.empty();
        }

        @Override
        public void putOwnerSuggestions(long ownerId, long generation, String query, int limit, List<LinkSuggestion> suggestions) {
        }

        @Override
        public Optional<LinkDiscoveryPage> getOwnerDiscoveryPage(long ownerId, long generation, LinkDiscoveryQuery query) {
            return Optional.empty();
        }

        @Override
        public void putOwnerDiscoveryPage(long ownerId, long generation, LinkDiscoveryQuery query, LinkDiscoveryPage page) {
        }

        @Override
        public long getOwnerAnalyticsGeneration(long ownerId) {
            return analyticsGenerationByOwner.getOrDefault(ownerId, 0L);
        }

        @Override
        public Optional<List<LinkActivityEvent>> getOwnerRecentActivity(long ownerId, long generation, int limit) {
            return Optional.empty();
        }

        @Override
        public void putOwnerRecentActivity(long ownerId, long generation, int limit, List<LinkActivityEvent> activityEvents) {
        }

        @Override
        public Optional<LinkTrafficSummary> getOwnerTrafficSummary(long ownerId, long generation, String slug) {
            return Optional.ofNullable(trafficSummaryCache.get(ownerId + ":" + generation + ":" + slug));
        }

        @Override
        public void putOwnerTrafficSummary(long ownerId, long generation, String slug, LinkTrafficSummary summary) {
            trafficSummaryCache.put(ownerId + ":" + generation + ":" + slug, summary);
        }

        @Override
        public Optional<List<TopLinkTraffic>> getOwnerTopLinks(long ownerId, long generation, LinkTrafficWindow window) {
            return Optional.ofNullable(topLinksCache.get(ownerId + ":" + generation + ":" + window.name()));
        }

        @Override
        public void putOwnerTopLinks(long ownerId, long generation, LinkTrafficWindow window, List<TopLinkTraffic> topLinks) {
            topLinksCache.put(ownerId + ":" + generation + ":" + window.name(), topLinks);
        }

        @Override
        public Optional<List<TrendingLink>> getOwnerTrendingLinks(long ownerId, long generation, LinkTrafficWindow window, int limit) {
            return Optional.ofNullable(trendingLinksCache.get(ownerId + ":" + generation + ":" + window.name() + ":" + limit));
        }

        @Override
        public void putOwnerTrendingLinks(long ownerId, long generation, LinkTrafficWindow window, int limit, List<TrendingLink> trendingLinks) {
            trendingLinksCache.put(ownerId + ":" + generation + ":" + window.name() + ":" + limit, trendingLinks);
        }

        @Override
        public void invalidateOwnerControlPlane(long ownerId) {
        }

        @Override
        public void invalidateOwnerAnalytics(long ownerId) {
            analyticsGenerationByOwner.merge(ownerId, 1L, Long::sum);
        }
    }
}
