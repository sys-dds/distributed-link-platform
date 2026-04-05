package com.linkplatform.api.link.application;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class RedirectClickAnalyticsConsumerTest {

    private static final String FREE_API_KEY = "free-owner-api-key";

    @Autowired
    private RedirectClickAnalyticsConsumer redirectClickAnalyticsConsumer;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @SpyBean
    private LinkReadCache linkReadCache;

    @Test
    void consumerProcessesClickEventAndUpdatesAnalyticsStorage() throws Exception {
        OffsetDateTime clickedAt = OffsetDateTime.parse("2026-04-03T09:00:00Z");
        insertLink("summary-link", "https://example.com/summary", clickedAt.minusDays(1));

        redirectClickAnalyticsConsumer.consume(objectMapper.writeValueAsString(new RedirectClickAnalyticsEvent(
                "event-1",
                "summary-link",
                clickedAt,
                "test-agent",
                "https://referrer.example",
                "127.0.0.1")));

        assertCount("SELECT COUNT(*) FROM link_clicks WHERE slug = 'summary-link'", 1);
        assertCount("SELECT click_count FROM link_click_daily_rollups WHERE slug = 'summary-link' AND rollup_date = DATE '2026-04-03'", 1);
        assertEquals(1.0, meterRegistry.get("link.analytics.consumer.processed").counter().count());
        assertEquals(0.0, meterRegistry.get("link.analytics.consumer.duplicate").counter().count());
    }

    @Test
    void reportingStillWorksAfterAsyncProcessing() throws Exception {
        OffsetDateTime clickedAt = OffsetDateTime.now().minusMinutes(10);
        insertLink("report-link", "https://example.com/report", clickedAt.minusDays(2));

        redirectClickAnalyticsConsumer.consume(objectMapper.writeValueAsString(new RedirectClickAnalyticsEvent(
                "event-2",
                "report-link",
                clickedAt,
                "test-agent",
                "https://referrer.example",
                "127.0.0.1")));

        mockMvc.perform(get("/api/v1/links/report-link/traffic-summary").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("report-link"))
                .andExpect(jsonPath("$.totalClicks").value(1))
                .andExpect(jsonPath("$.clicksLast24Hours").value(1))
                .andExpect(jsonPath("$.clicksLast7Days").value(1));
    }

    @Test
    void asyncProcessingPreservesTopLinksAndTrendingReporting() throws Exception {
        OffsetDateTime recent = OffsetDateTime.now().minusHours(1);
        OffsetDateTime previousWindow = recent.minusHours(26);
        insertLink("alpha-link", "https://example.com/alpha", recent.minusDays(2));
        insertLink("beta-link", "https://example.com/beta", recent.minusDays(2));

        redirectClickAnalyticsConsumer.consume(objectMapper.writeValueAsString(new RedirectClickAnalyticsEvent(
                "event-10",
                "alpha-link",
                recent,
                "test-agent",
                "https://referrer.example",
                "127.0.0.1")));
        redirectClickAnalyticsConsumer.consume(objectMapper.writeValueAsString(new RedirectClickAnalyticsEvent(
                "event-11",
                "alpha-link",
                recent.plusMinutes(1),
                "test-agent",
                "https://referrer.example",
                "127.0.0.1")));
        redirectClickAnalyticsConsumer.consume(objectMapper.writeValueAsString(new RedirectClickAnalyticsEvent(
                "event-12",
                "alpha-link",
                previousWindow,
                "test-agent",
                "https://referrer.example",
                "127.0.0.1")));
        redirectClickAnalyticsConsumer.consume(objectMapper.writeValueAsString(new RedirectClickAnalyticsEvent(
                "event-13",
                "beta-link",
                recent.plusMinutes(2),
                "test-agent",
                "https://referrer.example",
                "127.0.0.1")));

        mockMvc.perform(get("/api/v1/links/traffic/top").param("window", "24h").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("alpha-link"))
                .andExpect(jsonPath("$[0].clickTotal").value(2))
                .andExpect(jsonPath("$[1].slug").value("beta-link"))
                .andExpect(jsonPath("$[1].clickTotal").value(1));

        mockMvc.perform(get("/api/v1/links/traffic/trending").param("window", "24h").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("alpha-link"))
                .andExpect(jsonPath("$[0].clickGrowth").value(1))
                .andExpect(jsonPath("$[0].currentWindowClicks").value(2))
                .andExpect(jsonPath("$[0].previousWindowClicks").value(1));
    }

    @Test
    void consumerIsIdempotentForRedeliveredEvents() throws Exception {
        OffsetDateTime clickedAt = OffsetDateTime.parse("2026-04-03T09:15:00Z");
        RedirectClickAnalyticsEvent event = new RedirectClickAnalyticsEvent(
                "event-3",
                "idempotent-link",
                clickedAt,
                "test-agent",
                "https://referrer.example",
                "127.0.0.1");
        insertLink("idempotent-link", "https://example.com/idempotent", clickedAt.minusDays(1));

        String payloadJson = objectMapper.writeValueAsString(event);
        redirectClickAnalyticsConsumer.consume(payloadJson);
        redirectClickAnalyticsConsumer.consume(payloadJson);

        assertCount("SELECT COUNT(*) FROM link_clicks WHERE slug = 'idempotent-link'", 1);
        assertCount(
                "SELECT click_count FROM link_click_daily_rollups WHERE slug = 'idempotent-link' AND rollup_date = DATE '2026-04-03'",
                1);
        assertEquals(1.0, meterRegistry.get("link.analytics.consumer.processed").counter().count());
        assertEquals(1.0, meterRegistry.get("link.analytics.consumer.duplicate").counter().count());
    }

    @Test
    void processedClickInvalidatesOwnerAnalyticsCaches() throws Exception {
        OffsetDateTime clickedAt = OffsetDateTime.parse("2026-04-03T09:15:00Z");
        insertLink("invalidate-link", "https://example.com/invalidate", clickedAt.minusDays(1));

        redirectClickAnalyticsConsumer.consume(objectMapper.writeValueAsString(new RedirectClickAnalyticsEvent(
                "event-4",
                "invalidate-link",
                clickedAt,
                "test-agent",
                "https://referrer.example",
                "127.0.0.1")));

        verify(linkReadCache).invalidateOwnerAnalytics(1L);
    }

    private void insertLink(String slug, String originalUrl, OffsetDateTime createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO links (slug, original_url, created_at, hostname)
                VALUES (?, ?, ?, ?)
                """,
                slug,
                originalUrl,
                createdAt,
                java.net.URI.create(originalUrl).getHost().toLowerCase());
    }

    private void assertCount(String sql, int expectedCount) {
        Integer actualCount = jdbcTemplate.queryForObject(sql, Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(expectedCount, actualCount);
    }
}
