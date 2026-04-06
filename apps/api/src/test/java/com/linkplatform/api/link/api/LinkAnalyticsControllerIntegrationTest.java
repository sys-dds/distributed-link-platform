package com.linkplatform.api.link.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class LinkAnalyticsControllerIntegrationTest {

    private static final String FREE_API_KEY = "free-owner-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void richerTrafficSummaryReturnsReferrersBreakdownAndFreshTotals() throws Exception {
        jdbcTemplate.update(
                """
                INSERT INTO links (slug, original_url, created_at, hostname, version, owner_id, lifecycle_state)
                VALUES ('analytics-link', 'https://example.com/analytics', ?, 'example.com', 1, 1, 'ACTIVE')
                """,
                OffsetDateTime.parse("2026-04-01T08:00:00Z"));
        insertClick("analytics-link", "https://news.example");
        insertClick("analytics-link", "https://news.example");
        insertClick("analytics-link", null);

        mockMvc.perform(get("/api/v1/links/analytics-link/traffic-summary").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(3))
                .andExpect(jsonPath("$.topReferrers[0].referrer").value("https://news.example"))
                .andExpect(jsonPath("$.topReferrers[0].clickTotal").value(2))
                .andExpect(jsonPath("$.trafficBreakdown.ownerClicksLast24Hours").value(3))
                .andExpect(jsonPath("$.trafficBreakdown.ownerClicksLast7Days").value(3));
    }

    private void insertClick(String slug, String referrer) {
        jdbcTemplate.update(
                """
                INSERT INTO link_clicks (event_id, slug, clicked_at, user_agent, referrer, remote_address)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                slug + "-" + System.nanoTime(),
                slug,
                OffsetDateTime.now().minusMinutes(5),
                "test-agent",
                referrer,
                "127.0.0.1");
        jdbcTemplate.update(
                """
                MERGE INTO link_click_daily_rollups (slug, rollup_date, click_count)
                KEY (slug, rollup_date)
                VALUES (?, ?, COALESCE((SELECT click_count FROM link_click_daily_rollups WHERE slug = ? AND rollup_date = ?), 0) + 1)
                """,
                slug,
                OffsetDateTime.now().toLocalDate(),
                slug,
                OffsetDateTime.now().toLocalDate());
    }
}
