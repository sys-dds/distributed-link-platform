package com.linkplatform.api.link.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
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
    void backwardCompatibleWindowEndpointsAndSummaryStillWork() throws Exception {
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        insertLink("alpha", 1L, "ACTIVE", "team");
        insertLink("beta", 1L, "ACTIVE", "docs");
        insertClick("alpha", now.minusHours(2), "https://news.example");
        insertClick("alpha", now.minusDays(2), "https://news.example");
        insertClick("beta", now.minusDays(1), null);
        insertRollup("alpha", now.toLocalDate().minusDays(2), 1);
        insertRollup("alpha", now.toLocalDate(), 1);
        insertRollup("beta", now.toLocalDate().minusDays(1), 1);

        mockMvc.perform(get("/api/v1/links/traffic/top")
                        .param("window", "24h")
                        .header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("alpha"));

        mockMvc.perform(get("/api/v1/links/traffic/top")
                        .param("window", "7d")
                        .header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("alpha"));

        mockMvc.perform(get("/api/v1/links/traffic/trending")
                        .param("window", "24h")
                        .header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/links/traffic/trending")
                        .param("window", "7d")
                        .header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/links/alpha/traffic-summary").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(2))
                .andExpect(jsonPath("$.clicksLast24Hours").value(1))
                .andExpect(jsonPath("$.clicksLast7Days").value(2));

        mockMvc.perform(get("/api/v1/links/alpha").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abuseStatus").value("active"));
    }

    @Test
    void rejectsInvalidAnalyticsRangesAndLifecycleFilters() throws Exception {
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        insertLink("validate-me", 1L, "ACTIVE", "team");

        mockMvc.perform(get("/api/v1/links/validate-me/traffic-summary")
                        .param("to", now.toString())
                        .header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/links/validate-me/traffic-summary")
                        .param("from", now.minusHours(1).toString())
                        .header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/links/validate-me/traffic-summary")
                        .param("from", now.toString())
                        .param("to", now.toString())
                        .header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/links/validate-me/traffic-series")
                        .param("from", now.minusDays(8).toString())
                        .param("to", now.toString())
                        .param("granularity", "hour")
                        .header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/links/validate-me/traffic-summary")
                        .param("from", now.minusDays(91).toString())
                        .param("to", now.toString())
                        .header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trafficSummarySupportsRangeComparisonAndFreshness() throws Exception {
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        OffsetDateTime from = now.minusHours(6);
        OffsetDateTime to = now.minusHours(3);
        String fromText = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(from);
        String toText = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(to);
        insertLink("summary-link", 1L, "ACTIVE", "team");
        insertActivity("summary-link", 1L, now.minusMinutes(30), "ACTIVE", "team");
        insertClick("summary-link", from.plusMinutes(15), null);
        insertClick("summary-link", from.plusHours(1), null);
        insertClick("summary-link", from.minusHours(2), null);
        insertRollup("summary-link", now.toLocalDate(), 3);

                mockMvc.perform(get("/api/v1/links/summary-link/traffic-summary")
                        .param("from", fromText)
                        .param("to", toText)
                        .param("comparePrevious", "true")
                        .header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windowStart").value(fromText))
                .andExpect(jsonPath("$.windowEnd").value(toText))
                .andExpect(jsonPath("$.windowClicks").value(2))
                .andExpect(jsonPath("$.comparison.previousWindowStart").value(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(from.minusHours(3))))
                .andExpect(jsonPath("$.comparison.previousWindowEnd").value(fromText))
                .andExpect(jsonPath("$.comparison.currentWindowClicks").value(2))
                .andExpect(jsonPath("$.comparison.previousWindowClicks").value(1))
                .andExpect(jsonPath("$.comparison.clickChangeAbsolute").value(1))
                .andExpect(jsonPath("$.freshness.asOf").exists())
                .andExpect(jsonPath("$.freshness.latestMaterializedClickAt").exists())
                .andExpect(jsonPath("$.freshness.latestMaterializedActivityAt").exists());
    }

    @Test
    void trafficSeriesReturnsOrderedZeroFilledBucketsAndComparison() throws Exception {
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.HOURS);
        OffsetDateTime from = now.minusHours(4);
        OffsetDateTime to = now;
        String fromText = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(from);
        insertLink("series-link", 1L, "ACTIVE", "team");
        insertClick("series-link", from.plusMinutes(5), null);
        insertClick("series-link", from.plusHours(2).plusMinutes(10), null);
        insertClick("series-link", from.minusHours(2).plusMinutes(10), null);
        insertRollup("series-link", now.toLocalDate(), 3);

                mockMvc.perform(get("/api/v1/links/series-link/traffic-series")
                        .param("from", fromText)
                        .param("to", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(to))
                        .param("granularity", "hour")
                        .param("comparePrevious", "true")
                        .header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("series-link"))
                .andExpect(jsonPath("$.buckets[0].bucketStart").value(fromText))
                .andExpect(jsonPath("$.buckets[0].clickTotal").value(1))
                .andExpect(jsonPath("$.buckets[1].clickTotal").value(0))
                .andExpect(jsonPath("$.buckets[2].clickTotal").value(1))
                .andExpect(jsonPath("$.buckets[3].clickTotal").value(0))
                .andExpect(jsonPath("$.comparison.currentWindowClicks").value(2))
                .andExpect(jsonPath("$.comparison.previousWindowClicks").value(1));
    }

    @Test
    void filteredTopAndTrendingRespectTagLifecycleLimitAndOwnerScope() throws Exception {
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        OffsetDateTime from = now.minusHours(6);
        OffsetDateTime to = now.minusHours(3);
        insertLink("match-a", 1L, "ACTIVE", "team");
        insertLink("match-b", 1L, "ACTIVE", "team");
        insertLink("archived-link", 1L, "ARCHIVED", "team");
        insertLink("other-owner", 2L, "ACTIVE", "team");

        insertClick("match-a", from.plusMinutes(10), null);
        insertClick("match-a", from.plusMinutes(20), null);
        insertClick("match-a", from.minusHours(3).plusMinutes(10), null);
        insertClick("match-b", from.plusMinutes(30), null);
        insertClick("archived-link", from.plusMinutes(15), null);
        insertClick("other-owner", from.plusMinutes(15), null);

        mockMvc.perform(get("/api/v1/links/traffic/top")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("tag", "team")
                        .param("lifecycle", "active")
                        .param("limit", "1")
                        .header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("match-a"));

        mockMvc.perform(get("/api/v1/links/traffic/trending")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("tag", "team")
                        .param("lifecycle", "active")
                        .param("limit", "2")
                        .header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("match-a"))
                .andExpect(jsonPath("$[0].currentWindowClicks").value(2))
                .andExpect(jsonPath("$[0].previousWindowClicks").value(1))
                .andExpect(jsonPath("$[1].slug").value("match-b"));
    }

    @Test
    void filteredActivitySupportsTagLifecycleAndLimit() throws Exception {
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        insertLink("activity-active", 1L, "ACTIVE", "team");
        insertLink("activity-archived", 1L, "ARCHIVED", "team");
        insertActivity("activity-active", 1L, now.minusMinutes(5), "ACTIVE", "team");
        insertActivity("activity-archived", 1L, now.minusMinutes(1), "ARCHIVED", "team");

        mockMvc.perform(get("/api/v1/links/activity")
                        .param("tag", "team")
                        .param("lifecycle", "active")
                        .param("limit", "1")
                        .header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("activity-active"));
    }

    @Test
    void freshnessAndRangeTotalsUpdateAfterNewTraffic() throws Exception {
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        OffsetDateTime from = now.minusHours(2);
        insertLink("fresh-link", 1L, "ACTIVE", "team");
        insertActivity("fresh-link", 1L, now.minusMinutes(15), "ACTIVE", "team");
        insertClick("fresh-link", now.minusHours(1), null);
        insertRollup("fresh-link", now.toLocalDate(), 1);

        mockMvc.perform(get("/api/v1/links/fresh-link/traffic-summary")
                        .param("from", from.toString())
                        .param("to", now.toString())
                        .header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windowClicks").value(1));

        insertClick("fresh-link", now.minusMinutes(1), null);
        insertRollup("fresh-link", now.toLocalDate(), 2);

        mockMvc.perform(get("/api/v1/links/fresh-link/traffic-summary")
                        .param("from", from.toString())
                        .param("to", now.plusSeconds(1).toString())
                        .header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windowClicks").value(2))
                .andExpect(jsonPath("$.freshness.latestMaterializedClickAt").exists());
    }

    private void insertLink(String slug, long ownerId, String lifecycleState, String tag) {
        jdbcTemplate.update(
                """
                INSERT INTO links (slug, original_url, created_at, title, tags_json, hostname, version, owner_id, lifecycle_state)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                slug,
                "https://example.com/" + slug,
                OffsetDateTime.parse("2026-04-01T08:00:00Z"),
                slug,
                "[\"" + tag + "\"]",
                "example.com",
                1L,
                ownerId,
                lifecycleState);
        jdbcTemplate.update(
                "UPDATE links SET workspace_id = ?, abuse_status = 'ACTIVE' WHERE slug = ?",
                ownerId,
                slug);
        jdbcTemplate.update(
                """
                MERGE INTO link_catalog_projection (
                    slug, original_url, created_at, updated_at, title, tags_json, hostname, expires_at, lifecycle_state, deleted_at, version, owner_id, workspace_id
                ) KEY (slug)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                slug,
                "https://example.com/" + slug,
                OffsetDateTime.parse("2026-04-01T08:00:00Z"),
                OffsetDateTime.parse("2026-04-01T08:00:00Z"),
                slug,
                "[\"" + tag + "\"]",
                "example.com",
                null,
                lifecycleState,
                null,
                1L,
                ownerId,
                ownerId);
    }

    private void insertClick(String slug, OffsetDateTime clickedAt, String referrer) {
        jdbcTemplate.update(
                """
                INSERT INTO link_clicks (event_id, slug, clicked_at, user_agent, referrer, remote_address)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                slug + "-" + clickedAt.toInstant().toEpochMilli() + "-" + Math.abs(referrer == null ? 0 : referrer.hashCode()),
                slug,
                clickedAt,
                "test-agent",
                referrer,
                "127.0.0.1");
    }

    private void insertRollup(String slug, java.time.LocalDate day, long count) {
        jdbcTemplate.update(
                """
                MERGE INTO link_click_daily_rollups (slug, rollup_date, click_count)
                KEY (slug, rollup_date)
                VALUES (?, ?, ?)
                """,
                slug,
                day,
                count);
    }

    private void insertActivity(String slug, long ownerId, OffsetDateTime occurredAt, String lifecycleState, String tag) {
        jdbcTemplate.update(
                """
                INSERT INTO link_activity_events (
                    event_id, owner_id, workspace_id, event_type, slug, original_url, title, tags_json, hostname, expires_at, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                slug + "-activity-" + occurredAt.toInstant().toEpochMilli(),
                ownerId,
                ownerId,
                "UPDATED",
                slug,
                "https://example.com/" + slug,
                slug,
                "[\"" + tag + "\"]",
                "example.com",
                null,
                occurredAt);
        jdbcTemplate.update(
                """
                UPDATE link_catalog_projection
                SET lifecycle_state = ?
                WHERE slug = ?
                """,
                lifecycleState,
                slug);
    }
}
