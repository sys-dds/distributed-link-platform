package com.linkplatform.api.link.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class LinkAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void trafficSummaryReturnsSummaryForExistingLink() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime clickOne = now.minusMinutes(30);
        OffsetDateTime clickTwo = now.minusMinutes(20);
        OffsetDateTime clickThree = now.minusDays(3);
        OffsetDateTime clickFour = now.minusDays(6);
        LocalDate startDate = now.toLocalDate().minusDays(6);

        insertLink("summary-link", "https://example.com/summary", now.minusDays(10));
        insertClick("summary-link", clickOne);
        insertClick("summary-link", clickTwo);
        insertClick("summary-link", clickThree);
        insertClick("summary-link", clickFour);

        mockMvc.perform(get("/api/v1/links/summary-link/traffic-summary"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.slug").value("summary-link"))
                .andExpect(jsonPath("$.totalClicks").value(4))
                .andExpect(jsonPath("$.clicksLast24Hours").value(2))
                .andExpect(jsonPath("$.clicksLast7Days").value(4))
                .andExpect(jsonPath("$.recentDailyClicks.length()").value(7))
                .andExpect(jsonPath("$.recentDailyClicks[0].day").value(startDate.toString()))
                .andExpect(jsonPath("$.recentDailyClicks[6].day").value(now.toLocalDate().toString()))
                .andExpect(jsonPath("$.recentDailyClicks[0].clickTotal").value(1))
                .andExpect(jsonPath("$.recentDailyClicks[3].clickTotal").value(1))
                .andExpect(jsonPath("$.recentDailyClicks[6].clickTotal").value(2));
    }

    @Test
    void trafficSummaryReturnsNotFoundForMissingSlug() throws Exception {
        mockMvc.perform(get("/api/v1/links/missing-link/traffic-summary"))
                .andExpect(problemDetail(404, "Not Found", "Link slug not found: missing-link"));
    }

    @Test
    void recentActivityIncludesCreateUpdateAndDeleteEvents() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        insertActivity("CREATED", "alpha", "https://example.com/alpha", "Alpha", "[\"docs\"]", "example.com", null, now.minusMinutes(3));
        insertActivity("UPDATED", "alpha", "https://example.com/alpha-v2", "Alpha v2", "[\"docs\"]", "example.com", null, now.minusMinutes(2));
        insertActivity("DELETED", "alpha", "https://example.com/alpha-v2", "Alpha v2", "[\"docs\"]", "example.com", null, now.minusMinutes(1));

        mockMvc.perform(get("/api/v1/links/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].type").value("deleted"))
                .andExpect(jsonPath("$[0].slug").value("alpha"))
                .andExpect(jsonPath("$[1].type").value("updated"))
                .andExpect(jsonPath("$[2].type").value("created"));
    }

    @Test
    void deleteEventRemainsVisibleAfterLinkDeletion() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        insertActivity("DELETED", "gone-link", "https://example.com/gone", "Gone", "[\"archived\"]", "example.com", null, now);

        mockMvc.perform(get("/api/v1/links/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("deleted"))
                .andExpect(jsonPath("$[0].slug").value("gone-link"))
                .andExpect(jsonPath("$[0].originalUrl").value("https://example.com/gone"));
    }

    @Test
    void recentActivityUsesDeterministicOrderingForTies() throws Exception {
        OffsetDateTime sameTime = OffsetDateTime.now();
        insertActivity("CREATED", "alpha", "https://example.com/alpha", null, null, "example.com", null, sameTime);
        insertActivity("CREATED", "beta", "https://example.com/beta", null, null, "example.com", null, sameTime);

        mockMvc.perform(get("/api/v1/links/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("beta"))
                .andExpect(jsonPath("$[1].slug").value("alpha"));
    }

    @Test
    void topLinksRanksLinksForLast24Hours() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();

        insertLink("alpha", "https://example.com/alpha", now.minusDays(5));
        insertLink("beta", "https://example.com/beta", now.minusDays(5));
        insertLink("gamma", "https://example.com/gamma", now.minusDays(5));

        insertClick("alpha", now.minusHours(1));
        insertClick("alpha", now.minusHours(2));
        insertClick("beta", now.minusHours(3));
        insertClick("gamma", now.minusHours(30));

        mockMvc.perform(get("/api/v1/links/traffic/top").param("window", "24h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].slug").value("alpha"))
                .andExpect(jsonPath("$[0].clickTotal").value(2))
                .andExpect(jsonPath("$[1].slug").value("beta"))
                .andExpect(jsonPath("$[1].clickTotal").value(1));
    }

    @Test
    void topLinksUsesDeterministicOrderingForTies() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();

        insertLink("alpha", "https://example.com/alpha", now.minusDays(5));
        insertLink("beta", "https://example.com/beta", now.minusDays(5));

        insertClick("beta", now.minusDays(1));
        insertClick("alpha", now.minusDays(1));

        mockMvc.perform(get("/api/v1/links/traffic/top").param("window", "7d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].slug").value("alpha"))
                .andExpect(jsonPath("$[1].slug").value("beta"))
                .andExpect(jsonPath("$[0].clickTotal").value(1))
                .andExpect(jsonPath("$[1].clickTotal").value(1));
    }

    @Test
    void topLinksRejectsInvalidWindow() throws Exception {
        mockMvc.perform(get("/api/v1/links/traffic/top").param("window", "30d"))
                .andExpect(problemDetail(400, "Bad Request", "Window must be one of: 24h, 7d"));
    }

    @Test
    void trendingLinksRanksByRecentGrowth() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();

        insertLink("alpha", "https://example.com/alpha", now.minusDays(10));
        insertLink("beta", "https://example.com/beta", now.minusDays(10));

        insertClick("alpha", now.minusHours(1));
        insertClick("alpha", now.minusHours(2));
        insertClick("alpha", now.minusHours(3));
        insertClick("alpha", now.minusHours(30));

        insertClick("beta", now.minusHours(1));
        insertClick("beta", now.minusHours(2));
        insertClick("beta", now.minusHours(26));

        mockMvc.perform(get("/api/v1/links/traffic/trending").param("window", "24h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].slug").value("alpha"))
                .andExpect(jsonPath("$[0].clickGrowth").value(2))
                .andExpect(jsonPath("$[0].currentWindowClicks").value(3))
                .andExpect(jsonPath("$[0].previousWindowClicks").value(1))
                .andExpect(jsonPath("$[1].slug").value("beta"))
                .andExpect(jsonPath("$[1].clickGrowth").value(1))
                .andExpect(jsonPath("$[1].currentWindowClicks").value(2))
                .andExpect(jsonPath("$[1].previousWindowClicks").value(1));
    }

    @Test
    void trendingLinksUsesDeterministicTieBreaking() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();

        insertLink("alpha", "https://example.com/alpha", now.minusDays(10));
        insertLink("beta", "https://example.com/beta", now.minusDays(10));

        insertClick("beta", now.minusDays(1));
        insertClick("alpha", now.minusDays(1));

        mockMvc.perform(get("/api/v1/links/traffic/trending").param("window", "7d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].slug").value("alpha"))
                .andExpect(jsonPath("$[1].slug").value("beta"))
                .andExpect(jsonPath("$[0].clickGrowth").value(1))
                .andExpect(jsonPath("$[1].clickGrowth").value(1));
    }

    @Test
    void trendingLinksRejectInvalidWindow() throws Exception {
        mockMvc.perform(get("/api/v1/links/traffic/trending").param("window", "30d"))
                .andExpect(problemDetail(400, "Bad Request", "Window must be one of: 24h, 7d"));
    }

    private void insertLink(String slug, String originalUrl, OffsetDateTime createdAt) {
        jdbcTemplate.update(
                "INSERT INTO links (slug, original_url, created_at, hostname) VALUES (?, ?, ?, ?)",
                slug,
                originalUrl,
                createdAt,
                java.net.URI.create(originalUrl).getHost().toLowerCase());
    }

    private void insertActivity(
            String eventType,
            String slug,
            String originalUrl,
            String title,
            String tagsJson,
            String hostname,
            OffsetDateTime expiresAt,
            OffsetDateTime occurredAt) {
        jdbcTemplate.update(
                """
                INSERT INTO link_activity_events
                    (event_id, event_type, slug, original_url, title, tags_json, hostname, expires_at, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                slug + "-" + eventType + "-" + occurredAt.toInstant().toEpochMilli(),
                eventType,
                slug,
                originalUrl,
                title,
                tagsJson,
                hostname,
                expiresAt,
                occurredAt);
    }

    private void insertClick(String slug, OffsetDateTime clickedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO link_clicks (slug, clicked_at, user_agent, referrer, remote_address)
                VALUES (?, ?, ?, ?, ?)
                """,
                slug,
                clickedAt,
                "test-agent",
                "https://referrer.example",
                "127.0.0.1");

        int updated = jdbcTemplate.update(
                """
                UPDATE link_click_daily_rollups
                SET click_count = click_count + 1
                WHERE slug = ? AND rollup_date = ?
                """,
                slug,
                clickedAt.toLocalDate());

        if (updated == 0) {
            jdbcTemplate.update(
                    """
                    INSERT INTO link_click_daily_rollups (slug, rollup_date, click_count)
                    VALUES (?, ?, 1)
                    """,
                    slug,
                    clickedAt.toLocalDate());
        }
    }

    private static org.springframework.test.web.servlet.ResultMatcher problemDetail(
            int status, String title, String detail) {
        return result -> {
            status().is(status).match(result);
            content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON).match(result);
            jsonPath("$.type").value("about:blank").match(result);
            jsonPath("$.title").value(title).match(result);
            jsonPath("$.status").value(status).match(result);
            jsonPath("$.detail").value(detail).match(result);
            jsonPath("$.message").doesNotExist().match(result);
        };
    }
}
