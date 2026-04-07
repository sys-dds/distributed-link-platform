package com.linkplatform.api.link.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
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
        "link-platform.runtime.redirect.rate-limit.enabled=true",
        "link-platform.runtime.redirect.rate-limit.requests-per-window=1",
        "link-platform.runtime.redirect.rate-limit.window-seconds=60",
        "link-platform.abuse.auto-quarantine-threshold=2"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class RedirectAbuseAutoQuarantineIntegrationTest {

    private static final String FREE_API_KEY = "free-owner-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private StringRedisTemplate redisTemplate;

    private final Map<String, String> redisState = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Mockito.when(valueOperations.increment(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            long next = Long.parseLong(redisState.getOrDefault(key, "0")) + 1;
            redisState.put(key, Long.toString(next));
            return next;
        });
        Mockito.doAnswer(invocation -> null).when(valueOperations).set(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Duration.class));

        mockMvc.perform(post("/api/v1/links")
                        .header("X-API-Key", FREE_API_KEY)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"limited-abuse","originalUrl":"https://example.com/limited-abuse"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void repeatedRedirectRateLimitRejectionsOpenOneCaseIncrementSignalsAndAutoQuarantine() throws Exception {
        mockMvc.perform(get("/limited-abuse")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/limited-abuse")).andExpect(status().isTooManyRequests());
        mockMvc.perform(get("/limited-abuse")).andExpect(status().isTooManyRequests());
        mockMvc.perform(get("/limited-abuse")).andExpect(status().isNotFound());

        Long caseCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM link_abuse_cases WHERE slug = 'limited-abuse' AND source = 'REDIRECT_RATE_LIMIT'",
                Long.class);
        Long signalCount = jdbcTemplate.queryForObject(
                "SELECT signal_count FROM link_abuse_cases WHERE slug = 'limited-abuse' AND source = 'REDIRECT_RATE_LIMIT' AND status = 'OPEN'",
                Long.class);
        String abuseStatus = jdbcTemplate.queryForObject(
                "SELECT abuse_status FROM links WHERE slug = 'limited-abuse'",
                String.class);
        Long quarantinedEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM owner_security_events WHERE event_type = 'LINK_QUARANTINED'",
                Long.class);

        assertEquals(1L, caseCount);
        assertEquals(2L, signalCount);
        assertEquals("QUARANTINED", abuseStatus);
        assertEquals(1L, quarantinedEvents);
    }
}
