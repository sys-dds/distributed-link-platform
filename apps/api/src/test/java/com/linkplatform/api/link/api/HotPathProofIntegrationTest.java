package com.linkplatform.api.link.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkplatform.api.link.application.RedirectClickAnalyticsConsumer;
import com.linkplatform.api.link.application.RedirectClickAnalyticsEvent;
import com.linkplatform.api.projection.ProjectionJobRunner;
import com.linkplatform.api.projection.ProjectionJobService;
import com.linkplatform.api.projection.ProjectionJobType;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "link-platform.cache.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class HotPathProofIntegrationTest {

    private static final String PRO_API_KEY = "pro-owner-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedirectClickAnalyticsConsumer redirectClickAnalyticsConsumer;

    @Autowired
    private ProjectionJobService projectionJobService;

    @Autowired
    private ProjectionJobRunner projectionJobRunner;

    @SpyBean
    private com.linkplatform.api.link.application.LinkStore linkStore;

    @MockBean
    private StringRedisTemplate redisTemplate;

    private final Map<String, String> redisState = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
        org.mockito.Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        org.mockito.Mockito.when(valueOperations.get(anyString()))
                .thenAnswer(invocation -> redisState.get(invocation.getArgument(0)));
        org.mockito.Mockito.doAnswer(invocation -> {
                    redisState.put(invocation.getArgument(0), invocation.getArgument(1));
                    return null;
                })
                .when(valueOperations)
                .set(anyString(), anyString(), any(Duration.class));
        org.mockito.Mockito.when(valueOperations.increment(anyString()))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    long next = Long.parseLong(redisState.getOrDefault(key, "0")) + 1;
                    redisState.put(key, Long.toString(next));
                    return next;
                });
        org.mockito.Mockito.doAnswer(invocation -> {
                    redisState.remove(invocation.getArgument(0));
                    return null;
                })
                .when(redisTemplate)
                .delete(anyString());
    }

    @Test
    void redirectHotPathUsesCacheAfterFirstLookup() throws Exception {
        insertLink(1L, "redirect-hot", "https://example.com/redirect-hot", OffsetDateTime.parse("2026-04-01T08:00:00Z"));
        clearInvocations(linkStore);

        mockMvc.perform(get("/redirect-hot"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(header().string("Location", "https://example.com/redirect-hot"));
        mockMvc.perform(get("/redirect-hot"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(header().string("Location", "https://example.com/redirect-hot"));

        verify(linkStore, times(1)).findBySlug(eq("redirect-hot"), any());
    }

    @Test
    void ownerDiscoveryAndAnalyticsHotPathsReuseCachedResults() throws Exception {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-04-01T08:00:00Z");
        insertLink(2L, "query-hot", "https://docs.example.com/query-hot", createdAt);
        insertCatalogProjection(2L, "query-hot", "https://docs.example.com/query-hot", createdAt, "Query Hot", "[\"docs\"]");
        insertDiscoveryProjection(2L, "query-hot", "https://docs.example.com/query-hot", createdAt, "Query Hot", "[\"docs\"]");
        insertClick("query-hot", OffsetDateTime.parse("2026-04-06T08:00:00Z"));
        clearInvocations(linkStore);

        mockMvc.perform(get("/api/v1/links/discovery")
                        .param("search", "query")
                        .header("X-API-Key", PRO_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].slug").value("query-hot"));
        mockMvc.perform(get("/api/v1/links/discovery")
                        .param("search", "query")
                        .header("X-API-Key", PRO_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].slug").value("query-hot"));

        mockMvc.perform(get("/api/v1/links/query-hot/traffic-summary").header("X-API-Key", PRO_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(1));
        mockMvc.perform(get("/api/v1/links/query-hot/traffic-summary").header("X-API-Key", PRO_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(1));

        verify(linkStore, times(1)).searchDiscovery(any(), eq(2L), any());
        verify(linkStore, times(1)).findTrafficSummaryTotals(eq("query-hot"), any(), any(), eq(2L));
        verify(linkStore, times(1)).findRecentDailyClickBuckets(eq("query-hot"), any(), eq(2L));
    }

    @Test
    void analyticsCachesRefreshAfterClickDrivenInvalidation() throws Exception {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-04-01T08:00:00Z");
        insertLink(2L, "fresh-clicks", "https://docs.example.com/fresh-clicks", createdAt);
        insertCatalogProjection(2L, "fresh-clicks", "https://docs.example.com/fresh-clicks", createdAt, "Fresh Clicks", "[\"docs\"]");
        insertDiscoveryProjection(2L, "fresh-clicks", "https://docs.example.com/fresh-clicks", createdAt, "Fresh Clicks", "[\"docs\"]");
        insertActivity(2L, "fresh-clicks", "https://docs.example.com/fresh-clicks", createdAt, "Fresh Clicks", "[\"docs\"]");
        insertClick("fresh-clicks", OffsetDateTime.now().minusHours(2));

        mockMvc.perform(get("/api/v1/links/fresh-clicks/traffic-summary").header("X-API-Key", PRO_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(1));
        mockMvc.perform(get("/api/v1/links/traffic/top").param("window", "24h").header("X-API-Key", PRO_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clickTotal").value(1));
        mockMvc.perform(get("/api/v1/links/traffic/trending").param("window", "24h").header("X-API-Key", PRO_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("fresh-clicks"))
                .andExpect(jsonPath("$[0].currentWindowClicks").value(1));
        mockMvc.perform(get("/api/v1/links/activity").header("X-API-Key", PRO_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("created"))
                .andExpect(jsonPath("$[0].slug").value("fresh-clicks"));

        redirectClickAnalyticsConsumer.consume(objectMapper.writeValueAsString(new RedirectClickAnalyticsEvent(
                "fresh-event-2",
                "fresh-clicks",
                OffsetDateTime.now().minusHours(1),
                "test-agent",
                "https://referrer.example",
                "127.0.0.1")));

        mockMvc.perform(get("/api/v1/links/fresh-clicks/traffic-summary").header("X-API-Key", PRO_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(2));
        mockMvc.perform(get("/api/v1/links/traffic/top").param("window", "24h").header("X-API-Key", PRO_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clickTotal").value(2));
        mockMvc.perform(get("/api/v1/links/traffic/trending").param("window", "24h").header("X-API-Key", PRO_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("fresh-clicks"))
                .andExpect(jsonPath("$[0].currentWindowClicks").value(2));
        mockMvc.perform(get("/api/v1/links/activity").header("X-API-Key", PRO_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("clicked"))
                .andExpect(jsonPath("$[0].slug").value("fresh-clicks"));
    }

    @Test
    void analyticsCachesRefreshAfterRollupRebuildInvalidation() throws Exception {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-04-01T08:00:00Z");
        insertLink(2L, "rebuild-clicks", "https://docs.example.com/rebuild-clicks", createdAt);
        insertCatalogProjection(2L, "rebuild-clicks", "https://docs.example.com/rebuild-clicks", createdAt, "Rebuild Clicks", "[\"docs\"]");
        insertDiscoveryProjection(2L, "rebuild-clicks", "https://docs.example.com/rebuild-clicks", createdAt, "Rebuild Clicks", "[\"docs\"]");
        insertClick("rebuild-clicks", OffsetDateTime.now().minusDays(1));

        mockMvc.perform(get("/api/v1/links/traffic/top").param("window", "7d").header("X-API-Key", PRO_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clickTotal").value(1));
        mockMvc.perform(get("/api/v1/links/traffic/trending").param("window", "7d").header("X-API-Key", PRO_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("rebuild-clicks"))
                .andExpect(jsonPath("$[0].currentWindowClicks").value(1));
        mockMvc.perform(get("/api/v1/links/rebuild-clicks/traffic-summary").header("X-API-Key", PRO_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(1));

        jdbcTemplate.update("DELETE FROM link_click_daily_rollups");
        insertClickWithoutRollup("rebuild-clicks", OffsetDateTime.now().minusHours(1));

        projectionJobService.createJob(ProjectionJobType.CLICK_ROLLUP_REBUILD);
        projectionJobRunner.runPendingJobs();
        projectionJobRunner.runPendingJobs();

        mockMvc.perform(get("/api/v1/links/traffic/top").param("window", "7d").header("X-API-Key", PRO_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clickTotal").value(2));
        mockMvc.perform(get("/api/v1/links/traffic/trending").param("window", "7d").header("X-API-Key", PRO_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("rebuild-clicks"))
                .andExpect(jsonPath("$[0].currentWindowClicks").value(2));
        mockMvc.perform(get("/api/v1/links/rebuild-clicks/traffic-summary").header("X-API-Key", PRO_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(2));
    }

    private void insertLink(long ownerId, String slug, String originalUrl, OffsetDateTime createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO links (slug, original_url, created_at, hostname, version, owner_id)
                VALUES (?, ?, ?, ?, 1, ?)
                """,
                slug,
                originalUrl,
                createdAt,
                URI.create(originalUrl).getHost().toLowerCase(),
                ownerId);
    }

    private void insertCatalogProjection(
            long ownerId,
            String slug,
            String originalUrl,
            OffsetDateTime createdAt,
            String title,
            String tagsJson) {
        jdbcTemplate.update(
                """
                INSERT INTO link_catalog_projection (
                    slug, original_url, created_at, updated_at, title, tags_json, hostname, expires_at, deleted_at, version, owner_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, 1, ?)
                """,
                slug,
                originalUrl,
                createdAt,
                createdAt,
                title,
                tagsJson,
                URI.create(originalUrl).getHost().toLowerCase(),
                null,
                ownerId);
    }

    private void insertDiscoveryProjection(
            long ownerId,
            String slug,
            String originalUrl,
            OffsetDateTime createdAt,
            String title,
            String tagsJson) {
        jdbcTemplate.update(
                """
                INSERT INTO link_discovery_projection (
                    slug, owner_id, original_url, title, hostname, tags_json, created_at, updated_at, expires_at, deleted_at, lifecycle_state, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, 'ACTIVE', 1)
                """,
                slug,
                ownerId,
                originalUrl,
                title,
                URI.create(originalUrl).getHost().toLowerCase(),
                tagsJson,
                createdAt,
                createdAt,
                null);
    }

    private void insertClick(String slug, OffsetDateTime clickedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO link_clicks (event_id, slug, clicked_at, user_agent, referrer, remote_address)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                slug + "-" + clickedAt.toInstant().toEpochMilli(),
                slug,
                clickedAt,
                "test-agent",
                "https://referrer.example",
                "127.0.0.1");
        jdbcTemplate.update(
                """
                INSERT INTO link_click_daily_rollups (slug, rollup_date, click_count)
                VALUES (?, ?, 1)
                """,
                slug,
                clickedAt.toLocalDate());
    }

    private void insertClickWithoutRollup(String slug, OffsetDateTime clickedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO link_clicks (event_id, slug, clicked_at, user_agent, referrer, remote_address)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                slug + "-" + clickedAt.toInstant().toEpochMilli(),
                slug,
                clickedAt,
                "test-agent",
                "https://referrer.example",
                "127.0.0.1");
    }

    private void insertActivity(
            long ownerId,
            String slug,
            String originalUrl,
            OffsetDateTime occurredAt,
            String title,
            String tagsJson) {
        jdbcTemplate.update(
                """
                INSERT INTO link_activity_events (
                    event_id, owner_id, event_type, slug, original_url, title, tags_json, hostname, expires_at, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                slug + "-created",
                ownerId,
                "CREATED",
                slug,
                originalUrl,
                title,
                tagsJson,
                URI.create(originalUrl).getHost().toLowerCase(),
                null,
                occurredAt);
    }
}
