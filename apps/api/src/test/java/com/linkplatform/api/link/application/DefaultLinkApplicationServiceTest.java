package com.linkplatform.api.link.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linkplatform.api.link.domain.Link;
import com.linkplatform.api.link.domain.LinkSlug;
import com.linkplatform.api.link.domain.OriginalUrl;
import com.linkplatform.api.owner.application.AuthenticatedOwner;
import com.linkplatform.api.owner.application.OwnerPlan;
import com.linkplatform.api.owner.application.OwnerQuotaExceededException;
import com.linkplatform.api.owner.application.OwnerStore;
import com.linkplatform.api.owner.application.SecurityEventStore;
import com.linkplatform.api.owner.application.SecurityEventType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class DefaultLinkApplicationServiceTest {

    private static final AuthenticatedOwner FREE_OWNER =
            new AuthenticatedOwner(1L, "free-owner", "Free Owner", OwnerPlan.FREE);
    private static final AuthenticatedOwner PRO_OWNER =
            new AuthenticatedOwner(2L, "pro-owner", "Pro Owner", OwnerPlan.PRO);

    private final TestLinkStore linkStore = new TestLinkStore();
    private final TestAnalyticsOutboxStore analyticsOutboxStore = new TestAnalyticsOutboxStore();
    private final TestLinkLifecycleOutboxStore linkLifecycleOutboxStore = new TestLinkLifecycleOutboxStore();
    private final TestLinkMutationIdempotencyStore idempotencyStore = new TestLinkMutationIdempotencyStore();
    private final TestOwnerStore ownerStore = new TestOwnerStore();
    private final TestSecurityEventStore securityEventStore = new TestSecurityEventStore();
    private final TestLinkReadCache linkReadCache = new TestLinkReadCache();
    private final DefaultLinkApplicationService service = new DefaultLinkApplicationService(
            linkStore,
            analyticsOutboxStore,
            linkLifecycleOutboxStore,
            idempotencyStore,
            ownerStore,
            securityEventStore,
            linkReadCache,
            "http://localhost:80");

    @Test
    void createLinkAssignsOwnerAndWritesLifecycleEvent() {
        LinkMutationResult result = service.createLink(
                FREE_OWNER,
                new CreateLinkCommand("launch-page", "https://example.com/launch", null, "Launch", List.of("docs")),
                null);

        assertEquals("launch-page", result.slug());
        assertEquals(1L, result.version());
        assertEquals(1L, linkStore.ownerIdBySlug.get("launch-page"));
        assertEquals(LinkLifecycleEventType.CREATED, linkLifecycleOutboxStore.events().getFirst().eventType());
        assertEquals(1L, linkLifecycleOutboxStore.events().getFirst().ownerId());
    }

    @Test
    void ownerScopedReadsDoNotLeakCrossOwnerData() {
        service.createLink(FREE_OWNER, new CreateLinkCommand("free-link", "https://example.com/free", null, null, null), null);
        service.createLink(PRO_OWNER, new CreateLinkCommand("pro-link", "https://example.com/pro", null, null, null), null);

        assertEquals(1, service.listRecentLinks(FREE_OWNER, 20, null, LinkLifecycleState.ALL).size());
        assertEquals("free-link", service.getLink(FREE_OWNER, "free-link").slug());
        assertThrows(LinkNotFoundException.class, () -> service.getLink(FREE_OWNER, "pro-link"));
    }

    @Test
    void updateLinkRequiresMatchingOwnerAndIncrementsVersion() {
        service.createLink(FREE_OWNER, new CreateLinkCommand("editable", "https://example.com/original", null, null, null), null);

        LinkMutationResult result = service.updateLink(
                FREE_OWNER,
                "editable",
                "https://example.com/updated",
                null,
                "Updated",
                List.of("docs"),
                1L,
                null);

        assertEquals("https://example.com/updated", result.originalUrl());
        assertEquals(2L, result.version());
        assertThrows(
                LinkNotFoundException.class,
                () -> service.updateLink(PRO_OWNER, "editable", "https://example.com/nope", null, null, null, 2L, null));
    }

    @Test
    void deleteLinkRequiresMatchingOwner() {
        service.createLink(FREE_OWNER, new CreateLinkCommand("delete-me", "https://example.com/delete", null, null, null), null);

        assertThrows(LinkNotFoundException.class, () -> service.deleteLink(PRO_OWNER, "delete-me", 1L, null));

        LinkMutationResult deleted = service.deleteLink(FREE_OWNER, "delete-me", 1L, null);
        assertTrue(deleted.deleted());
        assertEquals(2L, deleted.version());
    }

    @Test
    void createQuotaIsEnforcedPerOwner() {
        service.createLink(FREE_OWNER, new CreateLinkCommand("one", "https://example.com/one", null, null, null), null);
        service.createLink(FREE_OWNER, new CreateLinkCommand("two", "https://example.com/two", null, null, null), null);

        assertThrows(
                OwnerQuotaExceededException.class,
                () -> service.createLink(FREE_OWNER, new CreateLinkCommand("three", "https://example.com/three", null, null, null), null));
        assertEquals(SecurityEventType.QUOTA_REJECTED, securityEventStore.events().getFirst().eventType());
    }

    @Test
    void idempotencyIsScopedByOwner() {
        LinkMutationResult freeResult = service.createLink(
                FREE_OWNER,
                new CreateLinkCommand("free-idempotent", "https://example.com/free", null, null, null),
                "same-key");
        LinkMutationResult proResult = service.createLink(
                PRO_OWNER,
                new CreateLinkCommand("pro-idempotent", "https://example.com/pro", null, null, null),
                "same-key");

        assertEquals("free-idempotent", freeResult.slug());
        assertEquals("pro-idempotent", proResult.slug());
        assertEquals(2, idempotencyStore.records.size());
    }

    @Test
    void recordRedirectClickStillWritesAnalyticsOutboxOnly() {
        service.createLink(FREE_OWNER, new CreateLinkCommand("docs", "https://example.com/docs", null, null, null), null);

        service.recordRedirectClick("docs", "test-agent", "https://referrer.example", "127.0.0.1");

        assertEquals(1, analyticsOutboxStore.events().size());
        assertEquals(0, service.getRecentActivity(FREE_OWNER, 10).size());
    }

    @Test
    void resolveLinkUsesRedirectCacheAfterFirstLookup() {
        service.createLink(FREE_OWNER, new CreateLinkCommand("cached-redirect", "https://example.com/cached", null, null, null), null);

        service.resolveLink("cached-redirect");
        service.resolveLink("cached-redirect");

        assertEquals(1, linkStore.resolveLookups.getOrDefault("cached-redirect", 0));
    }

    @Test
    void mutationsInvalidateRedirectAndOwnerControlPlaneCaches() {
        service.createLink(FREE_OWNER, new CreateLinkCommand("cached-link", "https://example.com/cached", null, null, null), null);
        service.updateLink(FREE_OWNER, "cached-link", "https://example.com/updated", null, null, null, 1L, null);
        service.deleteLink(FREE_OWNER, "cached-link", 2L, null);

        assertTrue(linkReadCache.invalidatedRedirects.contains("cached-link"));
        assertTrue(linkReadCache.invalidatedControlPlaneOwners.contains(FREE_OWNER.id()));
    }

    @Test
    void analyticsCachesRefreshAfterOwnerAnalyticsInvalidation() {
        service.createLink(FREE_OWNER, new CreateLinkCommand("metrics-link", "https://example.com/metrics", null, null, null), null);
        linkStore.trafficTotalsBySlug.put("metrics-link", new LinkTrafficSummaryTotals("metrics-link", "https://example.com/metrics", 1L, 1L, 1L));
        linkStore.dailyBucketsBySlug.put("metrics-link", List.of(new DailyClickBucket(LocalDate.parse("2026-04-01"), 1L)));
        linkStore.topLinksByOwner.put(FREE_OWNER.id(), List.of(new TopLinkTraffic("metrics-link", "https://example.com/metrics", 1L)));
        linkStore.trendingLinksByOwner.put(FREE_OWNER.id(), List.of(new TrendingLink("metrics-link", "https://example.com/metrics", 1L, 1L, 0L)));

        assertEquals(1L, service.getTrafficSummary(FREE_OWNER, "metrics-link").totalClicks());
        assertEquals(1L, service.getTopLinks(FREE_OWNER, LinkTrafficWindow.LAST_24_HOURS).getFirst().clickTotal());
        assertEquals(1L, service.getTrendingLinks(FREE_OWNER, LinkTrafficWindow.LAST_24_HOURS, 10).getFirst().clickGrowth());

        linkStore.trafficTotalsBySlug.put("metrics-link", new LinkTrafficSummaryTotals("metrics-link", "https://example.com/metrics", 3L, 3L, 3L));
        linkStore.dailyBucketsBySlug.put("metrics-link", List.of(new DailyClickBucket(LocalDate.parse("2026-04-01"), 3L)));
        linkStore.topLinksByOwner.put(FREE_OWNER.id(), List.of(new TopLinkTraffic("metrics-link", "https://example.com/metrics", 3L)));
        linkStore.trendingLinksByOwner.put(FREE_OWNER.id(), List.of(new TrendingLink("metrics-link", "https://example.com/metrics", 3L, 3L, 0L)));

        assertEquals(1L, service.getTrafficSummary(FREE_OWNER, "metrics-link").totalClicks());
        linkReadCache.invalidateOwnerAnalytics(FREE_OWNER.id());
        assertEquals(3L, service.getTrafficSummary(FREE_OWNER, "metrics-link").totalClicks());
        assertEquals(3L, service.getTopLinks(FREE_OWNER, LinkTrafficWindow.LAST_24_HOURS).getFirst().clickTotal());
        assertEquals(3L, service.getTrendingLinks(FREE_OWNER, LinkTrafficWindow.LAST_24_HOURS, 10).getFirst().clickGrowth());
    }

    @Test
    void staleAnalyticsWritesCannotRepopulateAfterInvalidation() {
        service.createLink(FREE_OWNER, new CreateLinkCommand("metrics-race", "https://example.com/metrics-race", null, null, null), null);
        linkStore.trafficTotalsBySlug.put("metrics-race", new LinkTrafficSummaryTotals("metrics-race", "https://example.com/metrics-race", 1L, 1L, 1L));
        linkStore.dailyBucketsBySlug.put("metrics-race", List.of(new DailyClickBucket(LocalDate.parse("2026-04-01"), 1L)));

        LinkTrafficSummary initial = service.getTrafficSummary(FREE_OWNER, "metrics-race");
        long staleGeneration = linkReadCache.getOwnerAnalyticsGeneration(FREE_OWNER.id());

        linkStore.trafficTotalsBySlug.put("metrics-race", new LinkTrafficSummaryTotals("metrics-race", "https://example.com/metrics-race", 4L, 4L, 4L));
        linkStore.dailyBucketsBySlug.put("metrics-race", List.of(new DailyClickBucket(LocalDate.parse("2026-04-01"), 4L)));
        linkReadCache.invalidateOwnerAnalytics(FREE_OWNER.id());
        linkReadCache.putOwnerTrafficSummary(FREE_OWNER.id(), staleGeneration, "metrics-race", initial);

        assertEquals(4L, service.getTrafficSummary(FREE_OWNER, "metrics-race").totalClicks());
    }

    @Test
    void staleRedirectWritesCannotRepopulateAfterInvalidation() {
        service.createLink(FREE_OWNER, new CreateLinkCommand("redirect-race", "https://example.com/original", null, null, null), null);
        service.resolveLink("redirect-race");
        long staleGeneration = linkReadCache.getPublicRedirectGeneration("redirect-race");

        service.updateLink(
                FREE_OWNER,
                "redirect-race",
                "https://example.com/updated",
                null,
                null,
                null,
                1L,
                null);
        linkReadCache.putPublicRedirect(
                "redirect-race",
                staleGeneration,
                new Link(new LinkSlug("redirect-race"), new OriginalUrl("https://example.com/original")));

        assertEquals("https://example.com/updated", service.resolveLink("redirect-race").originalUrl().value());
    }

    @Test
    void searchLinksUsesDiscoveryProjectionShape() {
        service.createLink(FREE_OWNER, new CreateLinkCommand("alpha-docs", "https://docs.example.com/alpha", null, "Alpha Docs", List.of("docs")), null);
        service.createLink(PRO_OWNER, new CreateLinkCommand("alpha-pro", "https://app.example.com/alpha", null, "Alpha App", List.of("product")), null);

        LinkDiscoveryPage page = service.searchLinks(FREE_OWNER, new LinkDiscoveryQuery(
                "alpha",
                "docs.example.com",
                "docs",
                LinkDiscoveryLifecycleFilter.ACTIVE,
                LinkDiscoveryExpirationFilter.ANY,
                LinkDiscoverySort.UPDATED_DESC,
                10,
                null));

        assertEquals(1, page.items().size());
        assertEquals("alpha-docs", page.items().getFirst().slug());
        assertEquals(LinkDiscoveryLifecycleState.ACTIVE, page.items().getFirst().lifecycleState());
        assertTrue(page.nextCursor() == null || !page.nextCursor().isBlank());
    }

    private static final class TestLinkStore implements LinkStore {

        private final Map<String, Link> linksBySlug = new ConcurrentHashMap<>();
        private final Map<String, OffsetDateTime> expiresAtBySlug = new ConcurrentHashMap<>();
        private final Map<String, Long> versionBySlug = new ConcurrentHashMap<>();
        private final Map<String, Long> ownerIdBySlug = new ConcurrentHashMap<>();
        private final Map<String, LinkMetadata> metadataBySlug = new ConcurrentHashMap<>();
        private final Map<String, Integer> resolveLookups = new ConcurrentHashMap<>();
        private final Map<String, LinkTrafficSummaryTotals> trafficTotalsBySlug = new ConcurrentHashMap<>();
        private final Map<String, List<DailyClickBucket>> dailyBucketsBySlug = new ConcurrentHashMap<>();
        private final Map<Long, List<TopLinkTraffic>> topLinksByOwner = new ConcurrentHashMap<>();
        private final Map<Long, List<TrendingLink>> trendingLinksByOwner = new ConcurrentHashMap<>();

        @Override
        public boolean save(
                Link link,
                OffsetDateTime expiresAt,
                String title,
                List<String> tags,
                String hostname,
                long version,
                long ownerId) {
            boolean inserted = linksBySlug.putIfAbsent(link.slug().value(), link) == null;
            if (!inserted) {
                return false;
            }
            if (expiresAt != null) {
                expiresAtBySlug.put(link.slug().value(), expiresAt);
            }
            versionBySlug.put(link.slug().value(), version);
            ownerIdBySlug.put(link.slug().value(), ownerId);
            metadataBySlug.put(link.slug().value(), new LinkMetadata(title, tags == null ? List.of() : List.copyOf(tags), hostname));
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
            if (!java.util.Objects.equals(versionBySlug.get(slug), expectedVersion)
                    || !java.util.Objects.equals(ownerIdBySlug.get(slug), ownerId)) {
                return false;
            }
            Link existing = linksBySlug.get(slug);
            if (existing == null) {
                return false;
            }
            linksBySlug.put(slug, new Link(existing.slug(), new OriginalUrl(originalUrl)));
            if (expiresAt == null) {
                expiresAtBySlug.remove(slug);
            } else {
                expiresAtBySlug.put(slug, expiresAt);
            }
            versionBySlug.put(slug, nextVersion);
            metadataBySlug.put(slug, new LinkMetadata(title, tags == null ? List.of() : List.copyOf(tags), hostname));
            return true;
        }

        @Override
        public boolean deleteBySlug(String slug, long expectedVersion, long ownerId) {
            if (!java.util.Objects.equals(versionBySlug.get(slug), expectedVersion)
                    || !java.util.Objects.equals(ownerIdBySlug.get(slug), ownerId)) {
                return false;
            }
            return linksBySlug.remove(slug) != null;
        }

        @Override
        public long countActiveLinksByOwner(long ownerId) {
            OffsetDateTime now = OffsetDateTime.now();
            return linksBySlug.keySet().stream()
                    .filter(slug -> java.util.Objects.equals(ownerIdBySlug.get(slug), ownerId))
                    .filter(slug -> {
                        OffsetDateTime expiresAt = expiresAtBySlug.get(slug);
                        return expiresAt == null || expiresAt.isAfter(now);
                    })
                    .count();
        }

        @Override
        public boolean recordClickIfAbsent(LinkClick linkClick) {
            return true;
        }

        @Override
        public boolean recordActivityIfAbsent(String eventId, LinkActivityEvent linkActivityEvent) {
            return false;
        }

        @Override
        public Optional<Long> findOwnerIdBySlug(String slug) {
            return Optional.ofNullable(ownerIdBySlug.get(slug));
        }

        @Override
        public List<Long> findOwnerIdsWithClickHistory() {
            return ownerIdBySlug.values().stream().distinct().sorted().toList();
        }

        @Override
        public long rebuildClickDailyRollups() {
            return 0;
        }

        @Override
        public void projectCatalogEvent(LinkLifecycleEvent linkLifecycleEvent) {
        }

        @Override
        public void projectDiscoveryEvent(LinkLifecycleEvent linkLifecycleEvent) {
        }

        @Override
        public void resetCatalogProjection() {
        }

        @Override
        public void resetDiscoveryProjection() {
        }

        @Override
        public List<LinkClickHistoryRecord> findClickHistoryChunkAfter(long afterId, int limit) {
            return List.of();
        }

        @Override
        public long applyClickHistoryChunkToDailyRollups(List<LinkClickHistoryRecord> clickHistoryChunk) {
            return clickHistoryChunk.size();
        }

        @Override
        public void resetClickDailyRollups() {
        }

        @Override
        public Optional<Link> findBySlug(String slug, OffsetDateTime now) {
            resolveLookups.merge(slug, 1, Integer::sum);
            if (isExpired(slug, now)) {
                return Optional.empty();
            }
            return Optional.ofNullable(linksBySlug.get(slug));
        }

        @Override
        public Optional<LinkDetails> findDetailsBySlug(String slug, OffsetDateTime now, long ownerId) {
            if (!java.util.Objects.equals(ownerIdBySlug.get(slug), ownerId) || isExpired(slug, now)) {
                return Optional.empty();
            }
            return findStoredDetailsBySlug(slug, ownerId)
                    .map(details -> new LinkDetails(
                            details.slug(),
                            details.originalUrl(),
                            details.createdAt(),
                            details.expiresAt(),
                            details.title(),
                            details.tags(),
                            details.hostname(),
                            details.version(),
                            0L));
        }

        @Override
        public Optional<LinkDetails> findStoredDetailsBySlug(String slug) {
            Long ownerId = ownerIdBySlug.get(slug);
            return ownerId == null ? Optional.empty() : findStoredDetailsBySlug(slug, ownerId);
        }

        @Override
        public Optional<LinkDetails> findStoredDetailsBySlug(String slug, long ownerId) {
            if (!java.util.Objects.equals(ownerIdBySlug.get(slug), ownerId)) {
                return Optional.empty();
            }
            Link link = linksBySlug.get(slug);
            if (link == null) {
                return Optional.empty();
            }
            LinkMetadata metadata = metadataBySlug.getOrDefault(slug, LinkMetadata.empty());
            return Optional.of(new LinkDetails(
                    link.slug().value(),
                    link.originalUrl().value(),
                    OffsetDateTime.parse("2026-04-01T08:00:00Z"),
                    expiresAtBySlug.get(slug),
                    metadata.title(),
                    metadata.tags(),
                    metadata.hostname(),
                    versionBySlug.getOrDefault(slug, 1L),
                    0L));
        }

        @Override
        public List<LinkDetails> findRecent(int limit, OffsetDateTime now, String query, LinkLifecycleState state, long ownerId) {
            return linksBySlug.values().stream()
                    .filter(link -> java.util.Objects.equals(ownerIdBySlug.get(link.slug().value()), ownerId))
                    .filter(link -> matchesState(link.slug().value(), now, state))
                    .filter(link -> matchesQuery(link, query))
                    .sorted(Comparator.comparing((Link link) -> link.slug().value()))
                    .limit(limit)
                    .map(link -> findStoredDetailsBySlug(link.slug().value(), ownerId).orElseThrow())
                    .toList();
        }

        @Override
        public List<LinkSuggestion> findSuggestions(int limit, OffsetDateTime now, String query, long ownerId) {
            return linksBySlug.values().stream()
                    .filter(link -> java.util.Objects.equals(ownerIdBySlug.get(link.slug().value()), ownerId))
                    .filter(link -> !isExpired(link.slug().value(), now))
                    .filter(link -> matchesQuery(link, query))
                    .sorted(Comparator.comparing(link -> link.slug().value()))
                    .limit(limit)
                    .map(link -> {
                        LinkMetadata metadata = metadataBySlug.getOrDefault(link.slug().value(), LinkMetadata.empty());
                        return new LinkSuggestion(link.slug().value(), metadata.title(), metadata.hostname());
                    })
                    .toList();
        }

        @Override
        public LinkDiscoveryPage searchDiscovery(OffsetDateTime now, long ownerId, LinkDiscoveryQuery query) {
            List<LinkDiscoveryItem> items = linksBySlug.values().stream()
                    .filter(link -> java.util.Objects.equals(ownerIdBySlug.get(link.slug().value()), ownerId))
                    .filter(link -> matchesDiscoveryQuery(link, query, now))
                    .sorted((left, right) -> switch (query.sort()) {
                        case UPDATED_DESC -> left.slug().value().compareTo(right.slug().value());
                        case CREATED_DESC -> left.slug().value().compareTo(right.slug().value());
                        case SLUG_ASC -> left.slug().value().compareTo(right.slug().value());
                    })
                    .limit(query.limit())
                    .map(link -> {
                        LinkMetadata metadata = metadataBySlug.getOrDefault(link.slug().value(), LinkMetadata.empty());
                        OffsetDateTime expiresAt = expiresAtBySlug.get(link.slug().value());
                        return new LinkDiscoveryItem(
                                link.slug().value(),
                                link.originalUrl().value(),
                                metadata.title(),
                                metadata.hostname(),
                                metadata.tags(),
                                expiresAt != null && !expiresAt.isAfter(now)
                                        ? LinkDiscoveryLifecycleState.EXPIRED
                                        : LinkDiscoveryLifecycleState.ACTIVE,
                                OffsetDateTime.parse("2026-04-01T08:00:00Z"),
                                OffsetDateTime.parse("2026-04-01T08:00:00Z"),
                                expiresAt,
                                null,
                                versionBySlug.getOrDefault(link.slug().value(), 1L));
                    })
                    .toList();
            return new LinkDiscoveryPage(items, items.isEmpty() ? null : "cursor", false);
        }

        @Override
        public List<LinkActivityEvent> findRecentActivity(int limit, long ownerId) {
            return List.of();
        }

        @Override
        public Optional<LinkTrafficSummaryTotals> findTrafficSummaryTotals(
                String slug,
                OffsetDateTime last24HoursSince,
                LocalDate last7DaysStartDate,
                long ownerId) {
            Link link = linksBySlug.get(slug);
            if (link == null || !java.util.Objects.equals(ownerIdBySlug.get(slug), ownerId)) {
                return Optional.empty();
            }
            return Optional.ofNullable(trafficTotalsBySlug.getOrDefault(
                    slug,
                    new LinkTrafficSummaryTotals(slug, link.originalUrl().value(), 0L, 0L, 0L)));
        }

        @Override
        public List<DailyClickBucket> findRecentDailyClickBuckets(String slug, LocalDate startDate, long ownerId) {
            return dailyBucketsBySlug.getOrDefault(slug, List.of(new DailyClickBucket(startDate, 0L)));
        }

        @Override
        public List<TopLinkTraffic> findTopLinks(LinkTrafficWindow window, OffsetDateTime now, long ownerId) {
            return topLinksByOwner.getOrDefault(ownerId, List.of());
        }

        @Override
        public List<TrendingLink> findTrendingLinks(LinkTrafficWindow window, OffsetDateTime now, int limit, long ownerId) {
            return trendingLinksByOwner.getOrDefault(ownerId, List.of());
        }

        private boolean isExpired(String slug, OffsetDateTime now) {
            OffsetDateTime expiresAt = expiresAtBySlug.get(slug);
            return expiresAt != null && !expiresAt.isAfter(now);
        }

        private boolean matchesState(String slug, OffsetDateTime now, LinkLifecycleState state) {
            return switch (state) {
                case ACTIVE -> !isExpired(slug, now);
                case EXPIRED -> isExpired(slug, now);
                case ALL -> true;
            };
        }

        private boolean matchesQuery(Link link, String query) {
            if (query == null || query.isBlank()) {
                return true;
            }
            String normalizedQuery = query.toLowerCase();
            LinkMetadata metadata = metadataBySlug.getOrDefault(link.slug().value(), LinkMetadata.empty());
            return link.slug().value().toLowerCase().contains(normalizedQuery)
                    || link.originalUrl().value().toLowerCase().contains(normalizedQuery)
                    || (metadata.title() != null && metadata.title().toLowerCase().contains(normalizedQuery))
                    || metadata.tags().stream().anyMatch(tag -> tag.toLowerCase().contains(normalizedQuery));
        }

        private boolean matchesDiscoveryQuery(Link link, LinkDiscoveryQuery query, OffsetDateTime now) {
            LinkMetadata metadata = metadataBySlug.getOrDefault(link.slug().value(), LinkMetadata.empty());
            OffsetDateTime expiresAt = expiresAtBySlug.get(link.slug().value());
            boolean lifecycleMatch = switch (query.lifecycle()) {
                case ACTIVE -> expiresAt == null || expiresAt.isAfter(now);
                case EXPIRED -> expiresAt != null && !expiresAt.isAfter(now);
                case DELETED -> false;
                case ALL -> true;
            };
            boolean expirationMatch = switch (query.expiration()) {
                case ANY -> true;
                case SCHEDULED -> expiresAt != null;
                case NONE -> expiresAt == null;
                case EXPIRED -> expiresAt != null && !expiresAt.isAfter(now);
            };
            boolean hostnameMatch = query.hostname() == null || query.hostname().isBlank()
                    || java.util.Objects.equals(metadata.hostname(), query.hostname());
            boolean tagMatch = query.tag() == null || query.tag().isBlank()
                    || metadata.tags().contains(query.tag());
            return lifecycleMatch
                    && expirationMatch
                    && hostnameMatch
                    && tagMatch
                    && matchesQuery(link, query.searchText());
        }
    }

    private record LinkMetadata(String title, List<String> tags, String hostname) {
        private static LinkMetadata empty() {
            return new LinkMetadata(null, List.of(), null);
        }
    }

    private static final class TestAnalyticsOutboxStore implements AnalyticsOutboxStore {
        private final List<RedirectClickAnalyticsEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void saveRedirectClickEvent(RedirectClickAnalyticsEvent redirectClickAnalyticsEvent) {
            events.add(redirectClickAnalyticsEvent);
        }

        @Override public long countUnpublished() { return events.size(); }
        @Override public long countEligible(OffsetDateTime now) { return events.size(); }
        @Override public long countParked() { return 0; }
        @Override public Double findOldestEligibleAgeSeconds(OffsetDateTime now) { return events.isEmpty() ? null : 0.0; }
        @Override public List<AnalyticsOutboxRecord> claimBatch(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil, int limit) { return List.of(); }
        @Override public void markPublished(long id, OffsetDateTime publishedAt) { }
        @Override public void recordPublishFailure(long id, int attemptCount, OffsetDateTime nextAttemptAt, String lastErrorSummary, OffsetDateTime parkedAt) { }
        @Override public List<AnalyticsOutboxRecord> findParked(int limit) { return List.of(); }
        @Override public boolean requeueParked(long id, OffsetDateTime nextAttemptAt) { return false; }
        @Override public long archivePublishedBefore(OffsetDateTime cutoff, int limit) { return 0; }
        @Override public long countArchived() { return 0; }

        private List<RedirectClickAnalyticsEvent> events() {
            return events;
        }
    }

    private static final class TestLinkMutationIdempotencyStore implements LinkMutationIdempotencyStore {
        private final Map<String, LinkMutationIdempotencyRecord> records = new ConcurrentHashMap<>();

        @Override
        public Optional<LinkMutationIdempotencyRecord> findByKey(long ownerId, String idempotencyKey) {
            return Optional.ofNullable(records.get(ownerId + ":" + idempotencyKey));
        }

        @Override
        public void saveResult(
                long ownerId,
                String idempotencyKey,
                String operation,
                String requestHash,
                LinkMutationResult result,
                OffsetDateTime createdAt) {
            records.put(
                    ownerId + ":" + idempotencyKey,
                    new LinkMutationIdempotencyRecord(ownerId, idempotencyKey, operation, requestHash, result, createdAt));
        }
    }

    private static final class TestLinkLifecycleOutboxStore implements LinkLifecycleOutboxStore {
        private final List<LinkLifecycleEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void saveLinkLifecycleEvent(LinkLifecycleEvent linkLifecycleEvent) {
            events.add(linkLifecycleEvent);
        }

        @Override public long countUnpublished() { return events.size(); }
        @Override public long countEligible(OffsetDateTime now) { return events.size(); }
        @Override public long countParked() { return 0; }
        @Override public Double findOldestEligibleAgeSeconds(OffsetDateTime now) { return events.isEmpty() ? null : 0.0; }
        @Override public List<LinkLifecycleOutboxRecord> claimBatch(String workerId, OffsetDateTime now, OffsetDateTime claimedUntil, int limit) { return List.of(); }
        @Override public void markPublished(long id, OffsetDateTime publishedAt) { }
        @Override public void recordPublishFailure(long id, int attemptCount, OffsetDateTime nextAttemptAt, String lastErrorSummary, OffsetDateTime parkedAt) { }
        @Override public List<LinkLifecycleOutboxRecord> findParked(int limit) { return List.of(); }
        @Override public List<LinkLifecycleEvent> findAllHistory() { return List.copyOf(events); }
        @Override public List<LinkLifecycleHistoryRecord> findHistoryChunkAfter(long afterId, int limit) { return List.of(); }
        @Override public boolean requeueParked(long id, OffsetDateTime nextAttemptAt) { return false; }

        private List<LinkLifecycleEvent> events() {
            return events;
        }
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
        private final List<SecurityEventRecord> events = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void record(
                SecurityEventType eventType,
                Long ownerId,
                String apiKeyHash,
                String requestMethod,
                String requestPath,
                String remoteAddress,
                String detailSummary,
                OffsetDateTime occurredAt) {
            events.add(new SecurityEventRecord(eventType, ownerId, detailSummary));
        }

        private List<SecurityEventRecord> events() {
            return events;
        }
    }

    private record SecurityEventRecord(SecurityEventType eventType, Long ownerId, String detailSummary) {
    }

    private static final class TestLinkReadCache implements LinkReadCache {
        private final Map<String, Link> redirectCache = new ConcurrentHashMap<>();
        private final Map<String, Long> redirectGenerationBySlug = new ConcurrentHashMap<>();
        private final List<String> invalidatedRedirects = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final List<Long> invalidatedControlPlaneOwners = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final Map<Long, Long> controlPlaneGenerationByOwner = new ConcurrentHashMap<>();
        private final Map<Long, Long> analyticsGenerationByOwner = new ConcurrentHashMap<>();

        @Override
        public long getPublicRedirectGeneration(String slug) {
            return redirectGenerationBySlug.getOrDefault(slug, 0L);
        }

        @Override
        public Optional<Link> getPublicRedirect(String slug, long generation) {
            return Optional.ofNullable(redirectCache.get(redirectCacheKey(slug, generation)));
        }

        @Override
        public void putPublicRedirect(String slug, long generation, Link link) {
            redirectCache.put(redirectCacheKey(slug, generation), link);
        }

        @Override
        public void invalidatePublicRedirect(String slug) {
            invalidatedRedirects.add(slug);
            redirectGenerationBySlug.merge(slug, 1L, Long::sum);
        }

        @Override public long getOwnerControlPlaneGeneration(long ownerId) { return controlPlaneGenerationByOwner.getOrDefault(ownerId, 0L); }
        @Override public Optional<LinkDetails> getOwnerLinkDetails(long ownerId, long generation, String slug) { return Optional.empty(); }
        @Override public void putOwnerLinkDetails(long ownerId, long generation, String slug, LinkDetails linkDetails) { }
        @Override public Optional<List<LinkDetails>> getOwnerRecentLinks(long ownerId, long generation, int limit, String query, LinkLifecycleState state) { return Optional.empty(); }
        @Override public void putOwnerRecentLinks(long ownerId, long generation, int limit, String query, LinkLifecycleState state, List<LinkDetails> linkDetails) { }
        @Override public Optional<List<LinkSuggestion>> getOwnerSuggestions(long ownerId, long generation, String query, int limit) { return Optional.empty(); }
        @Override public void putOwnerSuggestions(long ownerId, long generation, String query, int limit, List<LinkSuggestion> suggestions) { }
        @Override public Optional<LinkDiscoveryPage> getOwnerDiscoveryPage(long ownerId, long generation, LinkDiscoveryQuery query) { return Optional.empty(); }
        @Override public void putOwnerDiscoveryPage(long ownerId, long generation, LinkDiscoveryQuery query, LinkDiscoveryPage page) { }
        @Override public long getOwnerAnalyticsGeneration(long ownerId) { return analyticsGenerationByOwner.getOrDefault(ownerId, 0L); }

        private final Map<String, List<LinkActivityEvent>> recentActivityCache = new ConcurrentHashMap<>();
        private final Map<String, LinkTrafficSummary> trafficSummaryCache = new ConcurrentHashMap<>();
        private final Map<String, List<TopLinkTraffic>> topLinksCache = new ConcurrentHashMap<>();
        private final Map<String, List<TrendingLink>> trendingLinksCache = new ConcurrentHashMap<>();

        @Override public Optional<List<LinkActivityEvent>> getOwnerRecentActivity(long ownerId, long generation, int limit) { return Optional.ofNullable(recentActivityCache.get(ownerAnalyticsKey(ownerId, generation, String.valueOf(limit)))); }
        @Override public void putOwnerRecentActivity(long ownerId, long generation, int limit, List<LinkActivityEvent> activityEvents) { recentActivityCache.put(ownerAnalyticsKey(ownerId, generation, String.valueOf(limit)), activityEvents); }
        @Override public Optional<LinkTrafficSummary> getOwnerTrafficSummary(long ownerId, long generation, String slug) { return Optional.ofNullable(trafficSummaryCache.get(ownerAnalyticsKey(ownerId, generation, slug))); }
        @Override public void putOwnerTrafficSummary(long ownerId, long generation, String slug, LinkTrafficSummary summary) { trafficSummaryCache.put(ownerAnalyticsKey(ownerId, generation, slug), summary); }
        @Override public Optional<List<TopLinkTraffic>> getOwnerTopLinks(long ownerId, long generation, LinkTrafficWindow window) { return Optional.ofNullable(topLinksCache.get(ownerAnalyticsKey(ownerId, generation, window.name()))); }
        @Override public void putOwnerTopLinks(long ownerId, long generation, LinkTrafficWindow window, List<TopLinkTraffic> topLinks) { topLinksCache.put(ownerAnalyticsKey(ownerId, generation, window.name()), topLinks); }
        @Override public Optional<List<TrendingLink>> getOwnerTrendingLinks(long ownerId, long generation, LinkTrafficWindow window, int limit) { return Optional.ofNullable(trendingLinksCache.get(ownerAnalyticsKey(ownerId, generation, window.name() + ":" + limit))); }
        @Override public void putOwnerTrendingLinks(long ownerId, long generation, LinkTrafficWindow window, int limit, List<TrendingLink> trendingLinks) { trendingLinksCache.put(ownerAnalyticsKey(ownerId, generation, window.name() + ":" + limit), trendingLinks); }

        @Override
        public void invalidateOwnerControlPlane(long ownerId) {
            invalidatedControlPlaneOwners.add(ownerId);
            controlPlaneGenerationByOwner.merge(ownerId, 1L, Long::sum);
        }

        @Override
        public void invalidateOwnerAnalytics(long ownerId) {
            analyticsGenerationByOwner.merge(ownerId, 1L, Long::sum);
        }

        private String redirectCacheKey(String slug, long generation) {
            return slug + ":" + generation;
        }

        private String ownerAnalyticsKey(long ownerId, long generation, String suffix) {
            return ownerId + ":" + generation + ":" + suffix;
        }
    }
}
