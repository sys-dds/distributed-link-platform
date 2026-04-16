package com.linkplatform.api.link.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class LifecycleOutOfOrderEventIntegrationTest {

    @Autowired
    private LinkStore linkStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void olderLifecycleEventDeliveredAfterNewerEventDoesNotRollProjectionBackwards() {
        long workspaceId = personalWorkspaceId();
        OffsetDateTime now = OffsetDateTime.now();
        LinkLifecycleEvent newer = event(
                "out-of-order-newer",
                LinkLifecycleEventType.UPDATED,
                workspaceId,
                "out-of-order-link",
                "https://example.com/newer",
                "newer-title",
                2L,
                now.plusSeconds(10));
        LinkLifecycleEvent older = event(
                "out-of-order-older",
                LinkLifecycleEventType.CREATED,
                workspaceId,
                "out-of-order-link",
                "https://example.com/older",
                "older-title",
                1L,
                now);

        linkStore.projectCatalogEvent(newer);
        linkStore.projectDiscoveryEvent(newer);
        linkStore.projectCatalogEvent(older);
        linkStore.projectDiscoveryEvent(older);

        assertEquals(2L, queryLong("SELECT version FROM link_catalog_projection WHERE slug = 'out-of-order-link'"));
        assertEquals("https://example.com/newer", queryString("SELECT original_url FROM link_catalog_projection WHERE slug = 'out-of-order-link'"));
        assertEquals("newer-title", queryString("SELECT title FROM link_catalog_projection WHERE slug = 'out-of-order-link'"));
        assertTrue(queryTimestamp("SELECT updated_at FROM link_catalog_projection WHERE slug = 'out-of-order-link'")
                .toInstant()
                .isAfter(now.toInstant()));
        assertEquals(2L, queryLong("SELECT version FROM link_discovery_projection WHERE slug = 'out-of-order-link'"));
        assertEquals("https://example.com/newer", queryString("SELECT original_url FROM link_discovery_projection WHERE slug = 'out-of-order-link'"));
        assertTrue(queryTimestamp("SELECT updated_at FROM link_discovery_projection WHERE slug = 'out-of-order-link'")
                .toInstant()
                .isAfter(now.toInstant()));
    }

    private LinkLifecycleEvent event(
            String eventId,
            LinkLifecycleEventType eventType,
            long workspaceId,
            String slug,
            String originalUrl,
            String title,
            long version,
            OffsetDateTime occurredAt) {
        return new LinkLifecycleEvent(
                eventId,
                eventType,
                1L,
                workspaceId,
                slug,
                originalUrl,
                title,
                List.of("ordering"),
                "example.com",
                null,
                LinkLifecycleState.ACTIVE,
                version,
                occurredAt);
    }

    private long personalWorkspaceId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM workspaces WHERE created_by_owner_id = 1 AND personal_workspace = TRUE LIMIT 1",
                Long.class);
    }

    private long queryLong(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private String queryString(String sql) {
        return jdbcTemplate.queryForObject(sql, String.class);
    }

    private OffsetDateTime queryTimestamp(String sql) {
        return jdbcTemplate.queryForObject(sql, OffsetDateTime.class);
    }
}
