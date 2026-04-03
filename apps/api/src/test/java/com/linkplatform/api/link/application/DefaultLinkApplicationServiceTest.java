package com.linkplatform.api.link.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linkplatform.api.link.domain.Link;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class DefaultLinkApplicationServiceTest {

    private final TestLinkStore linkStore = new TestLinkStore();
    private final DefaultLinkApplicationService service =
            new DefaultLinkApplicationService(linkStore, "http://LOCALHOST:80");

    @Test
    void createsAndStoresValidatedLinkFromCommand() {
        Link link = service.createLink(new CreateLinkCommand("launch-page", "https://example.com/launch", null));

        assertEquals("launch-page", link.slug().value());
        assertEquals("https://example.com/launch", link.originalUrl().value());
    }

    @Test
    void failsWhenCommandContainsInvalidDomainData() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createLink(new CreateLinkCommand("no spaces allowed", "https://example.com", null)));
    }

    @Test
    void rejectsDuplicateSlug() {
        service.createLink(new CreateLinkCommand("repeatable", "https://example.com/one", null));

        assertThrows(DuplicateLinkSlugException.class,
                () -> service.createLink(new CreateLinkCommand("repeatable", "https://example.com/two", null)));
    }

    @Test
    void resolvesStoredLinkBySlug() {
        service.createLink(new CreateLinkCommand("docs", "https://example.com/docs", null));

        Link link = service.resolveLink("docs");

        assertEquals("docs", link.slug().value());
        assertEquals("https://example.com/docs", link.originalUrl().value());
    }

    @Test
    void rejectsMissingSlugDuringResolve() {
        assertThrows(LinkNotFoundException.class, () -> service.resolveLink("missing-link"));
    }

    @Test
    void returnsStoredLinkDetailsBySlug() {
        service.createLink(new CreateLinkCommand("details", "https://example.com/details", null));

        LinkDetails linkDetails = service.getLink("details");

        assertEquals("details", linkDetails.slug());
        assertEquals("https://example.com/details", linkDetails.originalUrl());
    }

    @Test
    void listsRecentLinksFromStore() {
        service.createLink(new CreateLinkCommand("alpha", "https://example.com/alpha", null));

        List<LinkDetails> recentLinks = service.listRecentLinks(10, null, LinkLifecycleState.ACTIVE);

        assertEquals(1, recentLinks.size());
        assertEquals("alpha", recentLinks.getFirst().slug());
    }

    @Test
    void updatesExistingLinkOriginalUrl() {
        service.createLink(new CreateLinkCommand("docs", "https://example.com/docs", null));

        LinkDetails linkDetails = service.updateLink("docs", "https://example.com/updated", null);

        assertEquals("docs", linkDetails.slug());
        assertEquals("https://example.com/updated", linkDetails.originalUrl());
    }

    @Test
    void deletesExistingLink() {
        service.createLink(new CreateLinkCommand("docs", "https://example.com/docs", null));

        service.deleteLink("docs");

        assertTrue(linkStore.deletedSlugs().containsKey("docs"));
    }

    @Test
    void storesFutureExpirationOnCreate() {
        OffsetDateTime expiresAt = OffsetDateTime.parse("2030-04-01T08:00:00Z");

        service.createLink(new CreateLinkCommand("expiring", "https://example.com/expiring", expiresAt));

        assertEquals(expiresAt, service.getLink("expiring").expiresAt());
    }

    @Test
    void recordsRedirectClick() {
        service.createLink(new CreateLinkCommand("docs", "https://example.com/docs", null));

        service.recordRedirectClick("docs", "test-agent", "https://referrer.example", "127.0.0.1");

        assertEquals(1, service.getLink("docs").clickTotal());
    }

    @Test
    void allowsPastExpirationAndTreatsLinkAsExpiredImmediately() {
        service.createLink(new CreateLinkCommand(
                "expired-now",
                "https://example.com/expired",
                OffsetDateTime.parse("2020-04-01T08:00:00Z")));

        assertThrows(LinkNotFoundException.class, () -> service.getLink("expired-now"));
        assertThrows(LinkNotFoundException.class, () -> service.resolveLink("expired-now"));
    }

    @Test
    void updatesExpiration() {
        service.createLink(new CreateLinkCommand("docs", "https://example.com/docs", null));
        OffsetDateTime expiresAt = OffsetDateTime.parse("2030-04-01T08:00:00Z");

        LinkDetails linkDetails = service.updateLink("docs", "https://example.com/docs", expiresAt);

        assertEquals(expiresAt, linkDetails.expiresAt());
    }

    @Test
    void rejectsReservedSlugCaseInsensitivelyBeforePersistence() {
        assertThrows(ReservedLinkSlugException.class,
                () -> service.createLink(new CreateLinkCommand("AcTuAtOr", "https://example.com/system", null)));

        assertEquals(0, linkStore.saveAttempts());
    }

    @Test
    void rejectsSelfTargetUrlBeforePersistenceIncludingDefaultPortEquivalence() {
        assertThrows(SelfTargetLinkException.class,
                () -> service.createLink(new CreateLinkCommand("self-loop", "http://localhost/about", null)));

        assertEquals(0, linkStore.saveAttempts());
    }

    private static final class TestLinkStore implements LinkStore {

        private final Map<String, Link> linksBySlug = new ConcurrentHashMap<>();
        private final Map<String, OffsetDateTime> expiresAtBySlug = new ConcurrentHashMap<>();
        private final Map<String, Long> clickTotalsBySlug = new ConcurrentHashMap<>();
        private final Map<String, Boolean> deletedSlugs = new ConcurrentHashMap<>();
        private int saveAttempts;

        @Override
        public boolean save(Link link, OffsetDateTime expiresAt) {
            saveAttempts++;
            boolean inserted = linksBySlug.putIfAbsent(link.slug().value(), link) == null;
            if (inserted && expiresAt != null) {
                expiresAtBySlug.put(link.slug().value(), expiresAt);
            }
            return inserted;
        }

        @Override
        public boolean update(String slug, String originalUrl, OffsetDateTime expiresAt) {
            Link updated = linksBySlug.computeIfPresent(
                    slug,
                    (ignored, link) -> new Link(link.slug(), new com.linkplatform.api.link.domain.OriginalUrl(originalUrl)));
            if (updated == null) {
                return false;
            }
            if (expiresAt == null) {
                expiresAtBySlug.remove(slug);
            } else {
                expiresAtBySlug.put(slug, expiresAt);
            }
            return true;
        }

        @Override
        public boolean deleteBySlug(String slug) {
            Link removed = linksBySlug.remove(slug);
            if (removed != null) {
                expiresAtBySlug.remove(slug);
                clickTotalsBySlug.remove(slug);
                deletedSlugs.put(slug, true);
                return true;
            }
            return false;
        }

        @Override
        public void recordClick(LinkClick linkClick) {
            clickTotalsBySlug.merge(linkClick.slug(), 1L, Long::sum);
        }

        @Override
        public Optional<Link> findBySlug(String slug, OffsetDateTime now) {
            if (isExpired(slug, now)) {
                return Optional.empty();
            }
            return Optional.ofNullable(linksBySlug.get(slug));
        }

        @Override
        public Optional<LinkDetails> findDetailsBySlug(String slug, OffsetDateTime now) {
            if (isExpired(slug, now)) {
                return Optional.empty();
            }
            Link link = linksBySlug.get(slug);
            if (link == null) {
                return Optional.empty();
            }

            return Optional.of(new LinkDetails(
                    link.slug().value(),
                    link.originalUrl().value(),
                    OffsetDateTime.parse("2026-04-01T08:00:00Z"),
                    expiresAtBySlug.get(slug),
                    clickTotalsBySlug.getOrDefault(slug, 0L)));
        }

        @Override
        public List<LinkDetails> findRecent(int limit, OffsetDateTime now, String query, LinkLifecycleState state) {
            return linksBySlug.values().stream()
                    .filter(link -> matchesState(link.slug().value(), now, state))
                    .filter(link -> matchesQuery(link, query))
                    .limit(limit)
                    .map(link -> new LinkDetails(
                            link.slug().value(),
                            link.originalUrl().value(),
                            OffsetDateTime.parse("2026-04-01T08:00:00Z"),
                            expiresAtBySlug.get(link.slug().value()),
                            clickTotalsBySlug.getOrDefault(link.slug().value(), 0L)))
                    .toList();
        }

        @Override
        public Optional<LinkTrafficSummaryTotals> findTrafficSummaryTotals(
                String slug,
                OffsetDateTime last24HoursSince,
                LocalDate last7DaysStartDate) {
            Link link = linksBySlug.get(slug);
            if (link == null) {
                return Optional.empty();
            }

            long clickTotal = clickTotalsBySlug.getOrDefault(slug, 0L);
            return Optional.of(new LinkTrafficSummaryTotals(
                    slug,
                    link.originalUrl().value(),
                    clickTotal,
                    clickTotal,
                    clickTotal));
        }

        @Override
        public List<DailyClickBucket> findRecentDailyClickBuckets(String slug, LocalDate startDate) {
            return List.of(new DailyClickBucket(startDate, clickTotalsBySlug.getOrDefault(slug, 0L)));
        }

        @Override
        public List<TopLinkTraffic> findTopLinks(LinkTrafficWindow window, OffsetDateTime now) {
            return linksBySlug.values().stream()
                    .map(link -> new TopLinkTraffic(
                            link.slug().value(),
                            link.originalUrl().value(),
                            clickTotalsBySlug.getOrDefault(link.slug().value(), 0L)))
                    .sorted(Comparator.comparingLong(TopLinkTraffic::clickTotal).reversed()
                            .thenComparing(TopLinkTraffic::slug))
                    .toList();
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
            return link.slug().value().toLowerCase().contains(normalizedQuery)
                    || link.originalUrl().value().toLowerCase().contains(normalizedQuery);
        }

        private boolean isExpired(String slug, OffsetDateTime now) {
            OffsetDateTime expiresAt = expiresAtBySlug.get(slug);
            return expiresAt != null && !expiresAt.isAfter(now);
        }

        private int saveAttempts() {
            return saveAttempts;
        }

        private Map<String, Boolean> deletedSlugs() {
            return deletedSlugs;
        }
    }
}
