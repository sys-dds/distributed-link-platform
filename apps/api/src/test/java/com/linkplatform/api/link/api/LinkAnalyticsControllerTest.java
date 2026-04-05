package com.linkplatform.api.link.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    private static final String FREE_API_KEY = "free-owner-api-key";
    private static final String PRO_API_KEY = "pro-owner-api-key";
    private static final long FREE_OWNER_ID = 1L;
    private static final long PRO_OWNER_ID = 2L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void recentActivityDoesNotLeakCrossOwnerData() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        insertActivity(FREE_OWNER_ID, "CREATED", "free-link", "https://example.com/free", "Free", "[\"docs\"]", "example.com", null, now.minusMinutes(2));
        insertActivity(PRO_OWNER_ID, "CREATED", "pro-link", "https://example.com/pro", "Pro", "[\"product\"]", "example.com", null, now.minusMinutes(1));

        mockMvc.perform(get("/api/v1/links/activity").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("free-link"));
    }

    @Test
    void trafficSummaryDoesNotLeakCrossOwnerData() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        insertLink(FREE_OWNER_ID, "summary-link", "https://example.com/summary", now.minusDays(10));
        insertLink(PRO_OWNER_ID, "other-link", "https://example.com/other", now.minusDays(10));
        insertClick("summary-link", now.minusHours(1));
        insertClick("other-link", now.minusHours(1));

        mockMvc.perform(get("/api/v1/links/summary-link/traffic-summary").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.slug").value("summary-link"))
                .andExpect(jsonPath("$.totalClicks").value(1));

        mockMvc.perform(get("/api/v1/links/other-link/traffic-summary").header("X-API-Key", FREE_API_KEY))
                .andExpect(problemDetail(404, "Not Found", "Link slug not found: other-link"));
    }

    @Test
    void topLinksAndTrendingDoNotLeakCrossOwnerData() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        insertLink(FREE_OWNER_ID, "alpha", "https://example.com/alpha", now.minusDays(5));
        insertLink(PRO_OWNER_ID, "beta", "https://example.com/beta", now.minusDays(5));
        insertClick("alpha", now.minusHours(1));
        insertClick("alpha", now.minusHours(2));
        insertClick("beta", now.minusHours(1));

        mockMvc.perform(get("/api/v1/links/traffic/top").param("window", "24h").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("alpha"))
                .andExpect(jsonPath("$[0].clickTotal").value(2));

        mockMvc.perform(get("/api/v1/links/traffic/trending").param("window", "24h").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("alpha"));
    }

    @Test
    void analyticsReadsRequireOwnerAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/links/activity"))
                .andExpect(problemDetail(401, "Unauthorized", "X-API-Key header is required"));
    }

    private void insertLink(long ownerId, String slug, String originalUrl, OffsetDateTime createdAt) {
        jdbcTemplate.update(
                "INSERT INTO links (slug, original_url, created_at, hostname, owner_id) VALUES (?, ?, ?, ?, ?)",
                slug,
                originalUrl,
                createdAt,
                java.net.URI.create(originalUrl).getHost().toLowerCase(),
                ownerId);
    }

    private void insertActivity(
            long ownerId,
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
                    (event_id, owner_id, event_type, slug, original_url, title, tags_json, hostname, expires_at, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                slug + "-" + eventType + "-" + occurredAt.toInstant().toEpochMilli(),
                ownerId,
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

    private static org.springframework.test.web.servlet.ResultMatcher problemDetail(int status, String title, String detail) {
        return result -> {
            status().is(status).match(result);
            content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON).match(result);
            jsonPath("$.type").value("about:blank").match(result);
            jsonPath("$.title").value(title).match(result);
            jsonPath("$.status").value(status).match(result);
            jsonPath("$.detail").value(detail).match(result);
        };
    }
}
