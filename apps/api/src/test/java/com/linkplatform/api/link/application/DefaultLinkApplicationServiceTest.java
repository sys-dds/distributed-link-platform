package com.linkplatform.api.link.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linkplatform.api.link.domain.Link;
import java.time.OffsetDateTime;
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
        Link link = service.createLink(new CreateLinkCommand("launch-page", "https://example.com/launch"));

        assertEquals("launch-page", link.slug().value());
        assertEquals("https://example.com/launch", link.originalUrl().value());
    }

    @Test
    void failsWhenCommandContainsInvalidDomainData() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createLink(new CreateLinkCommand("no spaces allowed", "https://example.com")));
    }

    @Test
    void rejectsDuplicateSlug() {
        service.createLink(new CreateLinkCommand("repeatable", "https://example.com/one"));

        assertThrows(DuplicateLinkSlugException.class,
                () -> service.createLink(new CreateLinkCommand("repeatable", "https://example.com/two")));
    }

    @Test
    void resolvesStoredLinkBySlug() {
        service.createLink(new CreateLinkCommand("docs", "https://example.com/docs"));

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
        service.createLink(new CreateLinkCommand("details", "https://example.com/details"));

        LinkDetails linkDetails = service.getLink("details");

        assertEquals("details", linkDetails.slug());
        assertEquals("https://example.com/details", linkDetails.originalUrl());
    }

    @Test
    void listsRecentLinksFromStore() {
        service.createLink(new CreateLinkCommand("alpha", "https://example.com/alpha"));

        List<LinkDetails> recentLinks = service.listRecentLinks(10);

        assertEquals(1, recentLinks.size());
        assertEquals("alpha", recentLinks.getFirst().slug());
    }

    @Test
    void updatesExistingLinkOriginalUrl() {
        service.createLink(new CreateLinkCommand("docs", "https://example.com/docs"));

        LinkDetails linkDetails = service.updateLink("docs", "https://example.com/updated");

        assertEquals("docs", linkDetails.slug());
        assertEquals("https://example.com/updated", linkDetails.originalUrl());
    }

    @Test
    void deletesExistingLink() {
        service.createLink(new CreateLinkCommand("docs", "https://example.com/docs"));

        service.deleteLink("docs");

        assertTrue(linkStore.deletedSlugs().containsKey("docs"));
    }

    @Test
    void rejectsReservedSlugCaseInsensitivelyBeforePersistence() {
        assertThrows(ReservedLinkSlugException.class,
                () -> service.createLink(new CreateLinkCommand("AcTuAtOr", "https://example.com/system")));

        assertEquals(0, linkStore.saveAttempts());
    }

    @Test
    void rejectsSelfTargetUrlBeforePersistenceIncludingDefaultPortEquivalence() {
        assertThrows(SelfTargetLinkException.class,
                () -> service.createLink(new CreateLinkCommand("self-loop", "http://localhost/about")));

        assertEquals(0, linkStore.saveAttempts());
    }

    private static final class TestLinkStore implements LinkStore {

        private final Map<String, Link> linksBySlug = new ConcurrentHashMap<>();
        private final Map<String, Boolean> deletedSlugs = new ConcurrentHashMap<>();
        private int saveAttempts;

        @Override
        public boolean save(Link link) {
            saveAttempts++;
            return linksBySlug.putIfAbsent(link.slug().value(), link) == null;
        }

        @Override
        public boolean updateOriginalUrl(String slug, String originalUrl) {
            return linksBySlug.computeIfPresent(
                    slug,
                    (ignored, link) -> new Link(link.slug(), new com.linkplatform.api.link.domain.OriginalUrl(originalUrl))) != null;
        }

        @Override
        public boolean deleteBySlug(String slug) {
            Link removed = linksBySlug.remove(slug);
            if (removed != null) {
                deletedSlugs.put(slug, true);
                return true;
            }
            return false;
        }

        @Override
        public Optional<Link> findBySlug(String slug) {
            return Optional.ofNullable(linksBySlug.get(slug));
        }

        @Override
        public Optional<LinkDetails> findDetailsBySlug(String slug) {
            Link link = linksBySlug.get(slug);
            if (link == null) {
                return Optional.empty();
            }

            return Optional.of(new LinkDetails(
                    link.slug().value(),
                    link.originalUrl().value(),
                    OffsetDateTime.parse("2026-04-01T08:00:00Z")));
        }

        @Override
        public List<LinkDetails> findRecent(int limit) {
            return linksBySlug.values().stream()
                    .limit(limit)
                    .map(link -> new LinkDetails(
                            link.slug().value(),
                            link.originalUrl().value(),
                            OffsetDateTime.parse("2026-04-01T08:00:00Z")))
                    .toList();
        }

        private int saveAttempts() {
            return saveAttempts;
        }

        private Map<String, Boolean> deletedSlugs() {
            return deletedSlugs;
        }
    }
}
