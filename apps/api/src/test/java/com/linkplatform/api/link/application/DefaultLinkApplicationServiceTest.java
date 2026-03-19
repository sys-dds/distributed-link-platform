package com.linkplatform.api.link.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.linkplatform.api.link.domain.Link;
import org.junit.jupiter.api.Test;

class DefaultLinkApplicationServiceTest {

    private final DefaultLinkApplicationService service = new DefaultLinkApplicationService();

    @Test
    void preparesValidatedLinkFromCommand() {
        Link link = service.prepareLink(new CreateLinkCommand("launch-page", "https://example.com/launch"));

        assertEquals("launch-page", link.slug().value());
        assertEquals("https://example.com/launch", link.originalUrl().value());
    }

    @Test
    void failsWhenCommandContainsInvalidDomainData() {
        assertThrows(IllegalArgumentException.class,
                () -> service.prepareLink(new CreateLinkCommand("no spaces allowed", "https://example.com")));
    }
}
