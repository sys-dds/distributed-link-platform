package com.linkplatform.api.link.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.linkplatform.api.link.domain.Link;
import org.junit.jupiter.api.Test;

class DefaultLinkApplicationServiceTest {

    private final DefaultLinkApplicationService service = new DefaultLinkApplicationService(new InMemoryLinkStore());

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
}
