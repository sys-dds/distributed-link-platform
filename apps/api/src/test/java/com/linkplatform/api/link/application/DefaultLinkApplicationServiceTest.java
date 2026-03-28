package com.linkplatform.api.link.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.linkplatform.api.link.domain.Link;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class DefaultLinkApplicationServiceTest {

    private final DefaultLinkApplicationService service = new DefaultLinkApplicationService(new TestLinkStore());

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

    private static final class TestLinkStore implements LinkStore {

        private final Map<String, Link> linksBySlug = new ConcurrentHashMap<>();

        @Override
        public boolean save(Link link) {
            return linksBySlug.putIfAbsent(link.slug().value(), link) == null;
        }

        @Override
        public Optional<Link> findBySlug(String slug) {
            return Optional.ofNullable(linksBySlug.get(slug));
        }
    }
}
