package com.linkplatform.api.link.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
class LifecycleDuplicateEventIntegrationTest {

    @Autowired
    private LinkStore linkStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void duplicateLifecycleEventDeliveryDoesNotCreateDuplicateDurableEffects() {
        long workspaceId = personalWorkspaceId();
        OffsetDateTime now = OffsetDateTime.now();
        LinkLifecycleEvent event = new LinkLifecycleEvent(
                "duplicate-event-1",
                LinkLifecycleEventType.CREATED,
                1L,
                workspaceId,
                "duplicate-event-link",
                "https://example.com/duplicate-event-link",
                "duplicate-title",
                List.of("duplicate"),
                "example.com",
                null,
                LinkLifecycleState.ACTIVE,
                1L,
                now);
        LinkActivityEvent activity = new LinkActivityEvent(
                event.ownerId(),
                event.workspaceId(),
                LinkActivityType.CREATED,
                event.slug(),
                event.originalUrl(),
                event.title(),
                event.tags(),
                event.hostname(),
                event.expiresAt(),
                event.occurredAt());

        assertTrue(linkStore.recordActivityIfAbsent(event.eventId(), activity));
        assertFalse(linkStore.recordActivityIfAbsent(event.eventId(), activity));
        linkStore.projectCatalogEvent(event);
        linkStore.projectCatalogEvent(event);
        linkStore.projectDiscoveryEvent(event);
        linkStore.projectDiscoveryEvent(event);

        assertEquals(1L, count("SELECT COUNT(*) FROM link_activity_events WHERE event_id = 'duplicate-event-1'"));
        assertEquals(1L, count("SELECT COUNT(*) FROM link_catalog_projection WHERE slug = 'duplicate-event-link'"));
        assertEquals(1L, count("SELECT COUNT(*) FROM link_discovery_projection WHERE slug = 'duplicate-event-link'"));
        assertEquals(1L, count("SELECT version FROM link_catalog_projection WHERE slug = 'duplicate-event-link'"));
        assertEquals(1L, count("SELECT version FROM link_discovery_projection WHERE slug = 'duplicate-event-link'"));
        assertEquals(1L, count("SELECT COUNT(DISTINCT event_id) FROM link_activity_events WHERE event_id = 'duplicate-event-1'"));
    }

    private long personalWorkspaceId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM workspaces WHERE created_by_owner_id = 1 AND personal_workspace = TRUE LIMIT 1",
                Long.class);
    }

    private long count(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }
}
