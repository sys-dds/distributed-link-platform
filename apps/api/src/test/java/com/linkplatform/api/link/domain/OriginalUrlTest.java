package com.linkplatform.api.link.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OriginalUrlTest {

    @Test
    void acceptsAbsoluteHttpUrl() {
        OriginalUrl url = new OriginalUrl("https://example.com/some/path");

        assertEquals("https://example.com/some/path", url.value());
    }

    @Test
    void rejectsRelativeUrl() {
        assertThrows(IllegalArgumentException.class, () -> new OriginalUrl("/relative/path"));
    }

    @Test
    void rejectsUnsupportedScheme() {
        assertThrows(IllegalArgumentException.class, () -> new OriginalUrl("ftp://example.com/file"));
    }
}
