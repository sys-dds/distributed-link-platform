package com.linkplatform.api.link.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "link-platform.runtime.mode=redirect",
        "link-platform.runtime.redirect.rate-limit.enabled=true",
        "link-platform.runtime.redirect.rate-limit.requests-per-window=2",
        "link-platform.runtime.redirect.rate-limit.window-seconds=60"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class RedirectRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private StringRedisTemplate redisTemplate;

    private final Map<String, String> redisState = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Mockito.when(valueOperations.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            long next = Long.parseLong(redisState.getOrDefault(key, "0")) + 1;
            redisState.put(key, Long.toString(next));
            return next;
        });
        Mockito.doAnswer(invocation -> null).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        jdbcTemplate.update(
                """
                INSERT INTO links (slug, original_url, created_at, hostname, version, owner_id, lifecycle_state)
                VALUES ('limited', 'https://example.com/limited', ?, 'example.com', 1, 1, 'ACTIVE')
                """,
                OffsetDateTime.parse("2026-04-01T08:00:00Z"));
    }

    @Test
    void repeatedAbuseGets429ProblemDetails() throws Exception {
        mockMvc.perform(get("/limited")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/limited")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("Public redirect rate limit exceeded"))
                .andExpect(jsonPath("$.category").value("redirect-rate-limit"));
    }
}
