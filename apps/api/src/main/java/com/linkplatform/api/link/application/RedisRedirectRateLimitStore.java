package com.linkplatform.api.link.application;

import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisRedirectRateLimitStore implements RedirectRateLimitStore {

    private final StringRedisTemplate redisTemplate;

    public RedisRedirectRateLimitStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public int increment(String subjectHash, String slug, OffsetDateTime bucketStartedAt, Duration window, OffsetDateTime expiresAt) {
        try {
            String key = "redirect-rate-limit:" + subjectHash + ":" + (slug == null ? "*" : slug) + ":" + bucketStartedAt.toInstant();
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.opsForValue().set(key, "1", window);
            }
            return count == null ? 0 : count.intValue();
        } catch (RuntimeException exception) {
            throw new DataAccessException("Redis redirect rate limit unavailable", exception) {
            };
        }
    }
}
