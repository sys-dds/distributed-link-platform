package com.linkplatform.api.link.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@SpringBootTest(properties = "link-platform.cache.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class LinkReadCacheFallbackIntegrationTest {

    private static final String FREE_API_KEY = "free-owner-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private StringRedisTemplate redisTemplate;

    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> mockedValueOperations = org.mockito.Mockito.mock(ValueOperations.class);
        this.valueOperations = mockedValueOperations;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("redis down"));
        org.mockito.Mockito.doThrow(new RuntimeException("redis down")).when(redisTemplate).delete(anyString());
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("redis down"));
        org.mockito.Mockito.doThrow(new RuntimeException("redis down"))
                .when(valueOperations)
                .set(anyString(), anyString(), any(java.time.Duration.class));
    }

    @Test
    void redisUnavailableFallsBackCleanlyForRedirectAndOwnerReads() throws Exception {
        insertOwnedLink(
                1L,
                "cached-link",
                "https://example.com/cached",
                OffsetDateTime.parse("2026-04-01T08:00:00Z"),
                null,
                "Cached Link",
                "[\"docs\"]");
        insertClick("cached-link", OffsetDateTime.parse("2026-04-02T08:00:00Z"));

        mockMvc.perform(get("/cached-link"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(header().string("Location", "https://example.com/cached"));

        mockMvc.perform(get("/api/v1/links/cached-link").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("cached-link"));

        mockMvc.perform(get("/api/v1/links/activity").header("X-API-Key", FREE_API_KEY))
                .andExpect(status().isOk());
    }

    private void insertOwnedLink(
            long ownerId,
            String slug,
            String originalUrl,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt,
            String title,
            String tagsJson) {
        String hostname = URI.create(originalUrl).getHost().toLowerCase();
        jdbcTemplate.update(
                """
                INSERT INTO links (slug, original_url, created_at, expires_at, title, tags_json, hostname, version, owner_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?)
                """,
                slug,
                originalUrl,
                createdAt,
                expiresAt,
                title,
                tagsJson,
                hostname,
                ownerId);
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
                hostname,
                expiresAt,
                ownerId);
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
                hostname,
                expiresAt,
                createdAt);
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
        jdbcTemplate.update(
                """
                INSERT INTO link_click_daily_rollups (slug, rollup_date, click_count)
                VALUES (?, ?, 1)
                """,
                slug,
                clickedAt.toLocalDate());
    }
}
