package com.linkplatform.api.link.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LinkSlugTest {

    @Test
    void acceptsValidSlug() {
        LinkSlug slug = new LinkSlug("hello-world_123");

        assertEquals("hello-world_123", slug.value());
    }

    @Test
    void trimsWhitespaceAroundSlug() {
        LinkSlug slug = new LinkSlug("  useful-slug  ");

        assertEquals("useful-slug", slug.value());
    }

    @Test
    void rejectsInvalidCharacters() {
        assertThrows(IllegalArgumentException.class, () -> new LinkSlug("bad slug!"));
    }
}
