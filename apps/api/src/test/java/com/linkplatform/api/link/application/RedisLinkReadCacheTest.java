package com.linkplatform.api.link.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkplatform.api.link.domain.Link;
import com.linkplatform.api.link.domain.LinkSlug;
import com.linkplatform.api.link.domain.OriginalUrl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisLinkReadCacheTest {

    @Test
    void publicRedirectCacheSupportsHitAndMiss() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ObjectMapper objectMapper = new ObjectMapper();
        RedisLinkReadCache cache = new RedisLinkReadCache(
                redisTemplate,
                objectMapper,
                new SimpleMeterRegistry(),
                Duration.ofMinutes(5),
                Duration.ofSeconds(30),
                Duration.ofSeconds(15));

        Link link = new Link(new LinkSlug("launch"), new OriginalUrl("https://example.com/launch"));
        when(valueOperations.get("link:redirect:launch"))
                .thenReturn(objectMapper.writeValueAsString(link))
                .thenReturn(null);

        assertTrue(cache.getPublicRedirect("launch").isPresent());
        assertTrue(cache.getPublicRedirect("launch").isEmpty());
    }

    @Test
    void ownerScopedCacheKeysDoNotCollideAcrossOwners() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        RedisLinkReadCache cache = new RedisLinkReadCache(
                redisTemplate,
                new ObjectMapper(),
                new SimpleMeterRegistry(),
                Duration.ofMinutes(5),
                Duration.ofSeconds(30),
                Duration.ofSeconds(15));

        cache.putOwnerLinkDetails(1L, "same-slug", new LinkDetails("same-slug", "https://example.com/one", null, null, null, List.of(), "example.com", 1L, 0L));
        cache.putOwnerLinkDetails(2L, "same-slug", new LinkDetails("same-slug", "https://example.com/two", null, null, null, List.of(), "example.com", 1L, 0L));
        cache.putOwnerRecentLinks(1L, 20, "same", LinkLifecycleState.ALL, List.of());
        cache.putOwnerRecentLinks(2L, 20, "same", LinkLifecycleState.ALL, List.of());
        cache.putOwnerSuggestions(1L, "same", 10, List.of());
        cache.putOwnerSuggestions(2L, "same", 10, List.of());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, org.mockito.Mockito.atLeast(6)).set(keyCaptor.capture(), any(String.class), any(Duration.class));
        List<String> keys = keyCaptor.getAllValues();

        assertTrue(keys.stream().anyMatch(key -> key.contains("link:owner:1:cp:v0:detail:same-slug")));
        assertTrue(keys.stream().anyMatch(key -> key.contains("link:owner:2:cp:v0:detail:same-slug")));
        assertTrue(keys.stream().anyMatch(key -> key.contains("link:owner:1:cp:v0:list:")));
        assertTrue(keys.stream().anyMatch(key -> key.contains("link:owner:2:cp:v0:list:")));
        assertTrue(keys.stream().anyMatch(key -> key.contains("link:owner:1:cp:v0:suggestions:")));
        assertTrue(keys.stream().anyMatch(key -> key.contains("link:owner:2:cp:v0:suggestions:")));
    }

    @Test
    void redisFailureFallsBackCleanlyAndRecordsDegradation() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any())).thenThrow(new RuntimeException("redis down"));
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RedisLinkReadCache cache = new RedisLinkReadCache(
                redisTemplate,
                new ObjectMapper(),
                meterRegistry,
                Duration.ofMinutes(5),
                Duration.ofSeconds(30),
                Duration.ofSeconds(15));

        assertTrue(cache.getOwnerTrafficSummary(1L, "launch").isEmpty());
        assertEquals(1.0, meterRegistry.get("link.cache.degraded").counter().count());
    }
}
