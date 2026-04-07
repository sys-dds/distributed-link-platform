package com.linkplatform.api.link.application;

import com.linkplatform.api.owner.application.SecurityEventStore;
import com.linkplatform.api.owner.application.SecurityEventType;
import com.linkplatform.api.runtime.LinkPlatformRuntimeProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class RedirectRateLimitService {

    private final RedirectRateLimitStore fallbackStore;
    private final RedirectRateLimitStore redisStore;
    private final SecurityEventStore securityEventStore;
    private final LinkAbuseReviewService linkAbuseReviewService;
    private final LinkPlatformRuntimeProperties runtimeProperties;
    private final Counter allowedCounter;
    private final Counter rejectedCounter;
    private final Counter degradedCounter;
    private final Counter fallbackCounter;
    private final Clock clock;
    private volatile String lastStoreMode = "startup";

    public RedirectRateLimitService(
            RedirectRateLimitStore fallbackStore,
            ObjectProvider<RedisRedirectRateLimitStore> redisStoreProvider,
            SecurityEventStore securityEventStore,
            LinkAbuseReviewService linkAbuseReviewService,
            LinkPlatformRuntimeProperties runtimeProperties,
            MeterRegistry meterRegistry) {
        this.fallbackStore = fallbackStore;
        this.redisStore = redisStoreProvider.getIfAvailable();
        this.securityEventStore = securityEventStore;
        this.linkAbuseReviewService = linkAbuseReviewService;
        this.runtimeProperties = runtimeProperties;
        this.allowedCounter = Counter.builder("link.redirect.rate_limit.allowed").register(meterRegistry);
        this.rejectedCounter = Counter.builder("link.redirect.rate_limit.rejected").register(meterRegistry);
        this.degradedCounter = Counter.builder("link.redirect.rate_limit.degraded_store").register(meterRegistry);
        this.fallbackCounter = Counter.builder("link.redirect.rate_limit.fallback_store").register(meterRegistry);
        this.clock = Clock.systemUTC();
    }

    public RedirectRateLimitDecision check(String slug, String requestPath, String remoteAddress) {
        if (!runtimeProperties.getRedirect().getRateLimit().isEnabled() || isTrustedInternalPath(requestPath)) {
            lastStoreMode = "bypass";
            return RedirectRateLimitDecision.allowed(false, false, 0, Integer.MAX_VALUE);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        Duration window = Duration.ofSeconds(runtimeProperties.getRedirect().getRateLimit().getWindowSeconds());
        int limit = runtimeProperties.getRedirect().getRateLimit().effectiveLimitForSlug(slug);
        OffsetDateTime bucketStart = now.truncatedTo(ChronoUnit.SECONDS)
                .minusSeconds(now.toEpochSecond() % runtimeProperties.getRedirect().getRateLimit().getWindowSeconds());
        String subjectHash = sha256(normalizeIp(remoteAddress));
        try {
            int count = increment(redisStore, subjectHash, slug, bucketStart, window, bucketStart.plus(window));
            allowedCounter.increment();
            lastStoreMode = "redis";
            if (count > limit) {
                rejectedCounter.increment();
                securityEventStore.record(
                        SecurityEventType.RATE_LIMIT_REJECTED,
                        null,
                        null,
                        "GET",
                        requestPath,
                        null,
                        "Redirect rate limit rejected",
                        now);
                linkAbuseReviewService.recordRedirectRateLimitSignal(slug);
                return RedirectRateLimitDecision.rejected(false, false, count, limit);
            }
            return RedirectRateLimitDecision.allowed(false, false, count, limit);
        } catch (RuntimeException exception) {
            degradedCounter.increment();
            int count = increment(fallbackStore, subjectHash, slug, bucketStart, window, bucketStart.plus(window));
            fallbackCounter.increment();
            lastStoreMode = "jdbc-fallback";
            if (count > limit) {
                rejectedCounter.increment();
                securityEventStore.record(
                        SecurityEventType.RATE_LIMIT_REJECTED,
                        null,
                        null,
                        "GET",
                        requestPath,
                        null,
                        "Redirect rate limit rejected",
                        now);
                linkAbuseReviewService.recordRedirectRateLimitSignal(slug);
                return RedirectRateLimitDecision.rejected(true, true, count, limit);
            }
            allowedCounter.increment();
            return RedirectRateLimitDecision.allowed(true, true, count, limit);
        }
    }

    public String getLastStoreMode() {
        return lastStoreMode;
    }

    private int increment(
            RedirectRateLimitStore store,
            String subjectHash,
            String slug,
            OffsetDateTime bucketStart,
            Duration window,
            OffsetDateTime expiresAt) {
        if (store == null) {
            throw new DataAccessException("Redirect rate limit store unavailable") {
            };
        }
        return store.increment(subjectHash, slug, bucketStart, window, expiresAt);
    }

    private boolean isTrustedInternalPath(String requestPath) {
        return "/actuator/health".equals(requestPath) || "/actuator/health/readiness".equals(requestPath) || "/actuator/ping".equals(requestPath);
    }

    private String normalizeIp(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return "unknown";
        }
        return remoteAddress.trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte part : hash) {
                hex.append(String.format("%02x", part));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}
