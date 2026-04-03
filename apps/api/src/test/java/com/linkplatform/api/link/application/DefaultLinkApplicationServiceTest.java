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
        Link link = service.createLink(new CreateLinkCommand("launch-page", "https://example.com/launch", null, null, null));

        assertEquals("launch-page", link.slug().value());
        assertEquals("https://example.com/launch", link.originalUrl().value());
    }

    @Test
    void failsWhenCommandContainsInvalidDomainData() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createLink(new CreateLinkCommand("no spaces allowed", "https://example.com", null, null, null)));
    }

    @Test
    void rejectsDuplicateSlug() {
        service.createLink(new CreateLinkCommand("repeatable", "https://example.com/one", null, null, null));

        assertThrows(DuplicateLinkSlugException.class,
                () -> service.createLink(new CreateLinkCommand("repeatable", "https://example.com/two", null, null, null)));
    }

    @Test
    void resolvesStoredLinkBySlug() {
        service.createLink(new CreateLinkCommand("docs", "https://example.com/docs", null, null, null));

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
        service.createLink(new CreateLinkCommand("details", "https://example.com/details", null, null, null));

        LinkDetails linkDetails = service.getLink("details");

        assertEquals("details", linkDetails.slug());
        assertEquals("https://example.com/details", linkDetails.originalUrl());
    }

    @Test
    void listsRecentLinksFromStore() {
        service.createLink(new CreateLinkCommand("alpha", "https://example.com/alpha", null, null, null));

        List<LinkDetails> recentLinks = service.listRecentLinks(10, null, LinkLifecycleState.ACTIVE);

        assertEquals(1, recentLinks.size());
        assertEquals("alpha", recentLinks.getFirst().slug());
    }

    @Test
    void updatesExistingLinkOriginalUrl() {
        service.createLink(new CreateLinkCommand("docs", "https://example.com/docs", null, null, null));

        LinkDetails linkDetails = service.updateLink("docs", "https://example.com/updated", null, null, null);

        assertEquals("docs", linkDetails.slug());
        assertEquals("https://example.com/updated", linkDetails.originalUrl());
    }

    @Test
    void deletesExistingLink() {
        service.createLink(new CreateLinkCommand("docs", "https://example.com/docs", null, null, null));

        service.deleteLink("docs");

        assertTrue(linkStore.deletedSlugs().containsKey("docs"));
    }

    @Test
    void storesFutureExpirationOnCreate() {
        OffsetDateTime expiresAt = OffsetDateTime.parse("2030-04-01T08:00:00Z");

        service.createLink(new CreateLinkCommand("expiring", "https://example.com/expiring", expiresAt, null, null));

        assertEquals(expiresAt, service.getLink("expiring").expiresAt());
    }

    @Test
    void storesNormalizedMetadataOnCreate() {
        service.createLink(new CreateLinkCommand(
                "tagged",
                "https://Docs.Example.com/guide",
                null,
                "  Launch Guide  ",
                List.of(" docs ", "product", "docs", "")));

        LinkDetails linkDetails = service.getLink("tagged");

        assertEquals("Launch Guide", linkDetails.title());
        assertEquals(List.of("docs", "product"), linkDetails.tags());
        assertEquals("docs.example.com", linkDetails.hostname());
    }

    @Test
    void recordsRedirectClick() {
        service.createLink(new CreateLinkCommand("docs", "https://example.com/docs", null, null, null));

        service.recordRedirectClick("docs", "test-agent", "https://referrer.example", "127.0.0.1");

        assertEquals(1, service.getLink("docs").clickTotal());
    }

    @Test
    void allowsPastExpirationAndTreatsLinkAsExpiredImmediately() {
        service.createLink(new CreateLinkCommand(
                "expired-now",
                "https://example.com/expired",
                OffsetDateTime.parse("2020-04-01T08:00:00Z"),
                null,
                null));

        assertThrows(LinkNotFoundException.class, () -> service.getLink("expired-now"));
        assertThrows(LinkNotFoundException.class, () -> service.resolveLink("expired-now"));
    }

    @Test
    void updatesExpiration() {
        service.createLink(new CreateLinkCommand("docs", "https://example.com/docs", null, null, null));
        OffsetDateTime expiresAt = OffsetDateTime.parse("2030-04-01T08:00:00Z");

        LinkDetails linkDetails = service.updateLink("docs", "https://example.com/docs", expiresAt, null, null);

        assertEquals(expiresAt, linkDetails.expiresAt());
    }

    @Test
    void updatesMetadata() {
        service.createLink(new CreateLinkCommand("docs", "https://example.com/docs", null, null, null));

        LinkDetails linkDetails = service.updateLink(
                "docs",
                "https://app.example.com/docs",
                null,
                "Docs Portal",
                List.of("portal", "docs"));

        assertEquals("Docs Portal", linkDetails.title());
        assertEquals(List.of("portal", "docs"), linkDetails.tags());
        assertEquals("app.example.com", linkDetails.hostname());
    }

    @Test
    void suggestsMatchingActiveLinks() {
        service.createLink(new CreateLinkCommand("alpha", "https://docs.example.com/alpha", null, "Alpha Docs", List.of("docs")));
        service.createLink(new CreateLinkCommand("beta", "https://app.example.com/beta", null, "Beta App", List.of("app")));

        List<LinkSuggestion> suggestions = service.suggestLinks("docs", 10);

        assertEquals(1, suggestions.size());
        assertEquals("alpha", suggestions.getFirst().slug());
    }

    @Test
    void rejectsReservedSlugCaseInsensitivelyBeforePersistence() {
        assertThrows(ReservedLinkSlugException.class,
                () -> service.createLink(new CreateLinkCommand("AcTuAtOr", "https://example.com/system", null, null, null)));

        assertEquals(0, linkStore.saveAttempts());
    }

    @Test
    void rejectsSelfTargetUrlBeforePersistenceIncludingDefaultPortEquivalence() {
        assertThrows(SelfTargetLinkException.class,
                () -> service.createLink(new CreateLinkCommand("self-loop", "http://localhost/about", null, null, null)));

        assertEquals(0, linkStore.saveAttempts());
    }

    private static final class TestLinkStore implements LinkStore {

        private final Map<String, Link> linksBySlug = new ConcurrentHashMap<>();
        private final Map<String, OffsetDateTime> expiresAtBySlug = new ConcurrentHashMap<>();
        private final Map<String, Long> clickTotalsBySlug = new ConcurrentHashMap<>();
        private final Map<String, Boolean> deletedSlugs = new ConcurrentHashMap<>();
        private int saveAttempts;

        @Override
        public boolean save(Link link, OffsetDateTime expiresAt, String title, List<String> tags, String hostname) {
            saveAttempts++;
            boolean inserted = linksBySlug.putIfAbsent(link.slug().value(), link) == null;
            if (inserted && expiresAt != null) {
                expiresAtBySlug.put(link.slug().value(), expiresAt);
            }
            if (inserted) {
                metadataBySlug.put(link.slug().value(), new LinkMetadata(title, tags == null ? List.of() : List.copyOf(tags), hostname));
            }
            return inserted;
        }

        @Override
        public boolean update(
                String slug,
                String originalUrl,
                OffsetDateTime expiresAt,
                String title,
                List<String> tags,
                String hostname) {
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
            metadataBySlug.put(slug, new LinkMetadata(title, tags == null ? List.of() : List.copyOf(tags), hostname));
            return true;
        }

        @Override
        public boolean deleteBySlug(String slug) {
            Link removed = linksBySlug.remove(slug);
            if (removed != null) {
                expiresAtBySlug.remove(slug);
                clickTotalsBySlug.remove(slug);
                metadataBySlug.remove(slug);
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
                    metadataBySlug.getOrDefault(slug, LinkMetadata.empty()).title(),
                    metadataBySlug.getOrDefault(slug, LinkMetadata.empty()).tags(),
                    metadataBySlug.getOrDefault(slug, LinkMetadata.empty()).hostname(),
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
                            metadataBySlug.getOrDefault(link.slug().value(), LinkMetadata.empty()).title(),
                            metadataBySlug.getOrDefault(link.slug().value(), LinkMetadata.empty()).tags(),
                            metadataBySlug.getOrDefault(link.slug().value(), LinkMetadata.empty()).hostname(),
                            clickTotalsBySlug.getOrDefault(link.slug().value(), 0L)))
                    .toList();
        }

        @Override
        public List<LinkSuggestion> findSuggestions(int limit, OffsetDateTime now, String query) {
            return linksBySlug.values().stream()
                    .filter(link -> !isExpired(link.slug().value(), now))
                    .filter(link -> matchesQuery(link, query))
                    .sorted(Comparator.comparing(link -> link.slug().value()))
                    .limit(limit)
                    .map(link -> new LinkSuggestion(
                            link.slug().value(),
                            metadataBySlug.getOrDefault(link.slug().value(), LinkMetadata.empty()).title(),
                            metadataBySlug.getOrDefault(link.slug().value(), LinkMetadata.empty()).hostname()))
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
            LinkMetadata metadata = metadataBySlug.getOrDefault(link.slug().value(), LinkMetadata.empty());
            return link.slug().value().toLowerCase().contains(normalizedQuery)
                    || link.originalUrl().value().toLowerCase().contains(normalizedQuery)
                    || (metadata.title() != null && metadata.title().toLowerCase().contains(normalizedQuery))
                    || metadata.tags().stream().anyMatch(tag -> tag.toLowerCase().contains(normalizedQuery))
                    || (metadata.hostname() != null && metadata.hostname().toLowerCase().contains(normalizedQuery));
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

        private final Map<String, LinkMetadata> metadataBySlug = new ConcurrentHashMap<>();
    }

    private record LinkMetadata(String title, List<String> tags, String hostname) {

        private static LinkMetadata empty() {
            return new LinkMetadata(null, List.of(), null);
        }
    }
}
