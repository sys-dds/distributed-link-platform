package com.linkplatform.api.runtime;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.OffsetDateTime;

public class RedirectRuntimeState {

    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Counter cacheBypassCounter;
    private final Counter cacheDegradedCounter;
    private final Counter primaryLookupSuccessCounter;
    private final Counter primaryLookupFailureCounter;
    private final Counter primaryFailureFailoverCounter;
    private final Counter primaryFailureUnavailableCounter;
    private final Counter failoverActivatedCounter;
    private final Counter unavailableCounter;
    private final Clock clock;
    private volatile OffsetDateTime lastPrimaryLookupFailureAt;
    private volatile String lastPrimaryLookupFailureReason = "none";
    private volatile OffsetDateTime lastFailoverAt;
    private volatile String lastDegradedPath = "none";
    private volatile OffsetDateTime lastDegradedAt;
    private volatile String lastDecision = "startup";

    public RedirectRuntimeState(MeterRegistry meterRegistry, Clock clock) {
        this.cacheHitCounter = Counter.builder("link.redirect.cache.hit")
                .description("Number of redirect cache hits")
                .register(meterRegistry);
        this.cacheMissCounter = Counter.builder("link.redirect.cache.miss")
                .description("Number of redirect cache misses")
                .register(meterRegistry);
        this.cacheBypassCounter = Counter.builder("link.redirect.cache.bypass")
                .description("Number of redirect requests that bypassed cache because cache generation tracking was degraded")
                .register(meterRegistry);
        this.cacheDegradedCounter = Counter.builder("link.redirect.cache.degraded")
                .description("Number of redirect requests affected by cache degradation")
                .register(meterRegistry);
        this.primaryLookupSuccessCounter = Counter.builder("link.redirect.primary.lookup.success")
                .description("Number of redirect primary lookup successes")
                .register(meterRegistry);
        this.primaryLookupFailureCounter = Counter.builder("link.redirect.primary.lookup.failure")
                .description("Number of redirect primary lookup failures")
                .register(meterRegistry);
        this.primaryFailureFailoverCounter = Counter.builder("link.redirect.primary.lookup.failure.failover")
                .description("Number of redirect primary lookup failures served via failover")
                .register(meterRegistry);
        this.primaryFailureUnavailableCounter = Counter.builder("link.redirect.primary.lookup.failure.unavailable")
                .description("Number of redirect primary lookup failures served as unavailable")
                .register(meterRegistry);
        this.failoverActivatedCounter = Counter.builder("link.redirect.failover.activated")
                .description("Number of redirect failover activations")
                .register(meterRegistry);
        this.unavailableCounter = Counter.builder("link.redirect.unavailable")
                .description("Number of redirect requests that could not be served or failed over")
                .register(meterRegistry);
        this.clock = clock;
    }

    public void recordCacheHit() {
        cacheHitCounter.increment();
        lastDecision = "cache-hit";
    }

    public void recordCacheMiss() {
        cacheMissCounter.increment();
        lastDecision = "cache-miss";
    }

    public void recordCacheBypass() {
        cacheBypassCounter.increment();
        lastDecision = "cache-bypass";
        lastDegradedPath = "cache-bypass";
        lastDegradedAt = OffsetDateTime.now(clock);
    }

    public void recordCacheDegraded(String reason) {
        cacheDegradedCounter.increment();
        lastPrimaryLookupFailureReason = reason;
        lastDecision = "cache-degraded";
        lastDegradedPath = "cache-degraded";
        lastDegradedAt = OffsetDateTime.now(clock);
    }

    public void recordPrimaryLookupSuccess() {
        primaryLookupSuccessCounter.increment();
        lastDecision = "primary-success";
    }

    public void recordPrimaryFailureFailover(String reason) {
        primaryLookupFailureCounter.increment();
        primaryFailureFailoverCounter.increment();
        lastPrimaryLookupFailureAt = OffsetDateTime.now(clock);
        lastPrimaryLookupFailureReason = reason;
        lastDecision = "primary-failure-failover";
    }

    public void recordPrimaryFailureUnavailable(String reason) {
        primaryLookupFailureCounter.increment();
        primaryFailureUnavailableCounter.increment();
        lastPrimaryLookupFailureAt = OffsetDateTime.now(clock);
        lastPrimaryLookupFailureReason = reason;
        lastDecision = "primary-failure-unavailable";
    }

    public void recordFailoverActivated() {
        failoverActivatedCounter.increment();
        lastFailoverAt = OffsetDateTime.now(clock);
        lastDecision = "failover";
    }

    public void recordUnavailable() {
        unavailableCounter.increment();
        lastDecision = "unavailable";
    }

    public OffsetDateTime getLastPrimaryLookupFailureAt() {
        return lastPrimaryLookupFailureAt;
    }

    public String getLastPrimaryLookupFailureReason() {
        return lastPrimaryLookupFailureReason;
    }

    public OffsetDateTime getLastFailoverAt() {
        return lastFailoverAt;
    }

    public String getLastDecision() {
        return lastDecision;
    }

    public String getLastDegradedPath() {
        return lastDegradedPath;
    }

    public OffsetDateTime getLastDegradedAt() {
        return lastDegradedAt;
    }
}
