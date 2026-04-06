package com.linkplatform.api.link.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import io.micrometer.core.instrument.MeterRegistry;

@SpringBootTest(properties = {
        "link-platform.runtime.mode=redirect",
        "link-platform.runtime.redirect.region=eu-west-1",
        "link-platform.runtime.redirect.failover-region=us-east-1",
        "link-platform.runtime.redirect.failover-base-url=http://localhost:8082",
        "link-platform.cache.enabled=true",
        "management.endpoint.health.show-details=always",
        "management.endpoint.health.group.readiness.show-details=always"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class RedirectRuntimeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @SpyBean
    private com.linkplatform.api.link.application.LinkStore linkStore;

    @MockBean
    private StringRedisTemplate redisTemplate;

    private final Map<String, String> redisState = new ConcurrentHashMap<>();

    @org.junit.jupiter.api.BeforeEach
    void setUpRedis() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
        org.mockito.Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        org.mockito.Mockito.when(valueOperations.get(anyString()))
                .thenAnswer(invocation -> redisState.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
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
        doAnswer(invocation -> {
                    redisState.remove(invocation.getArgument(0));
                    return null;
                })
                .when(redisTemplate)
                .delete(anyString());
    }

    @Test
    void redirectRuntimeServesPublicRedirectAndKeepsAsyncWriteShape() throws Exception {
        insertLink("runtime-link", "https://example.com/runtime");

        mockMvc.perform(get("/runtime-link"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(header().string("Location", "https://example.com/runtime"));

        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM analytics_outbox WHERE event_key = 'runtime-link'",
                        Integer.class));
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM link_clicks WHERE slug = 'runtime-link'",
                        Integer.class));
    }

    @Test
    void redirectRuntimeDoesNotExposeOwnerControlPlaneSurface() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/links"))
                .andExpect(status().isNotFound());
    }

    @Test
    void redirectRuntimeExposesRegionAwareReadinessPosture() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.runtimeRole.details.mode").value("REDIRECT"))
                .andExpect(jsonPath("$.components.queryDataSource.details.required").value(false))
                .andExpect(jsonPath("$.components.redirectRuntime.details.required").value(true))
                .andExpect(jsonPath("$.components.redirectRuntime.details.region").value("eu-west-1"))
                .andExpect(jsonPath("$.components.redirectRuntime.details.failoverConfigured").value(true))
                .andExpect(jsonPath("$.components.redirectRuntime.details.failoverRegion").value("us-east-1"))
                .andExpect(jsonPath("$.components.redirectRuntime.details.failoverBaseUrl").value("http://localhost:8082"))
                .andExpect(jsonPath("$.components.redirectRuntime.details.failoverReady").value(true));
    }

    @Test
    void redirectRuntimeUsesCachedRedirectWhenPrimaryLookupDegrades() throws Exception {
        insertLink("cached-runtime-link", "https://example.com/cached-runtime");

        mockMvc.perform(get("/cached-runtime-link"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(header().string("Location", "https://example.com/cached-runtime"));

        clearInvocations(linkStore);
        doThrow(new DataAccessResourceFailureException("primary unavailable"))
                .when(linkStore)
                .findBySlug(org.mockito.ArgumentMatchers.eq("cached-runtime-link"), any());

        mockMvc.perform(get("/cached-runtime-link"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(header().string("Location", "https://example.com/cached-runtime"));

        verify(linkStore, never()).findBySlug(org.mockito.ArgumentMatchers.eq("cached-runtime-link"), any());
    }

    @Test
    void redirectRuntimeFailsOverOnPrimaryLookupFailureAndSkipsAnalyticsWrite() throws Exception {
        doThrow(new DataAccessResourceFailureException("primary unavailable"))
                .when(linkStore)
                .findBySlug(org.mockito.ArgumentMatchers.eq("failover-runtime-link"), any());

        mockMvc.perform(get("/failover-runtime-link").queryParam("src", "campaign"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(header().string("Location", "http://localhost:8082/failover-runtime-link?src=campaign"));

        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM analytics_outbox WHERE event_key = 'failover-runtime-link'",
                        Integer.class));
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM owner_security_events WHERE event_type = 'REDIRECT_LOOKUP_FAILED'",
                        Integer.class));
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM owner_security_events WHERE event_type = 'REDIRECT_FAILOVER_ACTIVATED'",
                        Integer.class));
        assertEquals(1.0, meterRegistry.get("link.redirect.primary.lookup.failure").counter().count());
        assertEquals(1.0, meterRegistry.get("link.redirect.failover.activated").counter().count());

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.redirectRuntime.details.lastDecision").value("failover"))
                .andExpect(jsonPath("$.components.redirectRuntime.details.lastPrimaryLookupFailureReason")
                        .value("DataAccessResourceFailureException: primary unavailable"))
                .andExpect(jsonPath("$.components.redirectRuntime.details.lastFailoverAt").exists());
    }

    private static org.springframework.test.web.servlet.ResultMatcher problemDetail(int status, String title, String detail) {
        return result -> {
            status().is(status).match(result);
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                    .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                    .match(result);
            jsonPath("$.type").value("about:blank").match(result);
            jsonPath("$.title").value(title).match(result);
            jsonPath("$.status").value(status).match(result);
            jsonPath("$.detail").value(detail).match(result);
        };
    }

    private void insertLink(String slug, String originalUrl) {
        jdbcTemplate.update(
                """
                INSERT INTO links (slug, original_url, created_at, hostname, version, owner_id)
                VALUES (?, ?, ?, ?, 1, 1)
                """,
                slug,
                originalUrl,
                OffsetDateTime.parse("2026-04-01T08:00:00Z"),
                java.net.URI.create(originalUrl).getHost().toLowerCase());
    }
}
