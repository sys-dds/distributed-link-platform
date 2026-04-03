package com.linkplatform.api.link.application;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class RedirectClickAnalyticsConsumerTest {

    @Autowired
    private RedirectClickAnalyticsConsumer redirectClickAnalyticsConsumer;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

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

        mockMvc.perform(get("/api/v1/links/report-link/traffic-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("report-link"))
                .andExpect(jsonPath("$.totalClicks").value(1))
                .andExpect(jsonPath("$.clicksLast24Hours").value(1))
                .andExpect(jsonPath("$.clicksLast7Days").value(1));
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
