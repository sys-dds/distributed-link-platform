package com.linkplatform.api.link.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class LinkLifecycleConsumerTest {

    @Autowired
    private LinkLifecycleConsumer consumer;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Test
    void consumerProjectsLifecycleEventIntoExistingActivityFeed() throws Exception {
        LinkLifecycleEvent event = new LinkLifecycleEvent(
                "event-1",
                LinkLifecycleEventType.CREATED,
                "launch-page",
                "https://example.com/launch",
                "Launch",
                List.of("docs"),
                "example.com",
                null,
                OffsetDateTime.parse("2026-04-04T09:00:00Z"));

        consumer.consume(objectMapper.writeValueAsString(event));

        mockMvc.perform(get("/api/v1/links/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("created"))
                .andExpect(jsonPath("$[0].slug").value("launch-page"))
                .andExpect(jsonPath("$[0].title").value("Launch"));
        assertEquals(
                "https://example.com/launch",
                jdbcTemplate.queryForObject(
                        "SELECT original_url FROM link_catalog_projection WHERE slug = 'launch-page'",
                        String.class));
        assertEquals(1.0, meterRegistry.get("link.lifecycle.consumer.processed").counter().count());
    }

    @Test
    void consumerIsIdempotentForRedeliveredLifecycleEvents() throws Exception {
        LinkLifecycleEvent event = new LinkLifecycleEvent(
                "event-2",
                LinkLifecycleEventType.UPDATED,
                "launch-page",
                "https://example.com/launch-v2",
                "Launch v2",
                List.of("docs"),
                "example.com",
                OffsetDateTime.parse("2030-04-01T08:00:00Z"),
                OffsetDateTime.parse("2026-04-04T09:00:00Z"));
        String payload = objectMapper.writeValueAsString(event);

        consumer.consume(payload);
        consumer.consume(payload);

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM link_activity_events WHERE event_id = 'event-2'", Integer.class);
        assertEquals(1, count);
        Integer catalogCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM link_catalog_projection WHERE slug = 'launch-page'", Integer.class);
        assertEquals(1, catalogCount);
        assertEquals(1.0, meterRegistry.get("link.lifecycle.consumer.processed").counter().count());
        assertEquals(1.0, meterRegistry.get("link.lifecycle.consumer.duplicate").counter().count());
    }

    @Test
    void deletedLinkLifecycleEventKeepsSnapshotUsefulAfterDeletion() throws Exception {
        LinkLifecycleEvent event = new LinkLifecycleEvent(
                "event-3",
                LinkLifecycleEventType.DELETED,
                "gone-link",
                "https://example.com/gone",
                "Gone",
                List.of("archived"),
                "example.com",
                null,
                OffsetDateTime.parse("2026-04-04T09:00:00Z"));

        consumer.consume(objectMapper.writeValueAsString(event));

        mockMvc.perform(get("/api/v1/links/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("deleted"))
                .andExpect(jsonPath("$[0].slug").value("gone-link"))
                .andExpect(jsonPath("$[0].originalUrl").value("https://example.com/gone"))
                .andExpect(jsonPath("$[0].title").value("Gone"));
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM link_catalog_projection WHERE slug = 'gone-link' AND deleted_at IS NOT NULL",
                        Integer.class));
    }

    @Test
    void expirationUpdatedLifecycleEventPreservesStableFeedBehavior() throws Exception {
        LinkLifecycleEvent event = new LinkLifecycleEvent(
                "event-4",
                LinkLifecycleEventType.EXPIRATION_UPDATED,
                "expiring-link",
                "https://example.com/expiring",
                "Expiring",
                List.of("docs"),
                "example.com",
                OffsetDateTime.parse("2030-04-01T08:00:00Z"),
                OffsetDateTime.parse("2026-04-04T09:00:00Z"));

        consumer.consume(objectMapper.writeValueAsString(event));

        mockMvc.perform(get("/api/v1/links/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("updated"))
                .andExpect(jsonPath("$[0].expiresAt").value("2030-04-01T08:00:00Z"));
    }
}
