package com.linkplatform.api.link.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest(properties = {
        "link-platform.runtime.mode=redirect",
        "link-platform.runtime.redirect.region=eu-west-1",
        "link-platform.cache.enabled=true",
        "management.endpoint.health.show-details=always",
        "management.endpoint.health.group.readiness.show-details=always"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class RedirectRuntimeNoFailoverIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @SpyBean
    private com.linkplatform.api.link.application.LinkStore linkStore;

    @MockBean
    private StringRedisTemplate redisTemplate;

    private final Map<String, String> redisState = new ConcurrentHashMap<>();

    @BeforeEach
    void setUpRedis() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
        org.mockito.Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        org.mockito.Mockito.when(valueOperations.get(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> redisState.get(invocation.getArgument(0)));
        org.mockito.Mockito.doAnswer(invocation -> {
                    redisState.put(invocation.getArgument(0), invocation.getArgument(1));
                    return null;
                })
                .when(valueOperations)
                .set(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), any(Duration.class));
        org.mockito.Mockito.when(valueOperations.increment(org.mockito.ArgumentMatchers.anyString()))
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
                .delete(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void redirectRuntimeReturnsServiceUnavailableWhenPrimaryLookupFailsWithoutFailover() throws Exception {
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("primary unavailable"))
                .when(linkStore)
                .findBySlug(org.mockito.ArgumentMatchers.eq("runtime-unavailable"), any());

        mockMvc.perform(get("/runtime-unavailable"))
                .andExpect(problemDetail(
                        503,
                        "Service Unavailable",
                        "Redirect lookup temporarily unavailable for slug: runtime-unavailable"));

        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM analytics_outbox WHERE event_key = 'runtime-unavailable'",
                        Integer.class));
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM owner_security_events WHERE event_type = 'REDIRECT_LOOKUP_FAILED'",
                        Integer.class));
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM owner_security_events WHERE event_type = 'REDIRECT_FAILOVER_ACTIVATED'",
                        Integer.class));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.redirectRuntime.details.failoverConfigured").value(false))
                .andExpect(jsonPath("$.components.redirectRuntime.details.lastDecision").value("unavailable"));
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
}
