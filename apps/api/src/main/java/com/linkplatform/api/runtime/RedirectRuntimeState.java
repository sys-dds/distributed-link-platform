package com.linkplatform.api.runtime;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.OffsetDateTime;

public class RedirectRuntimeState {

    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Counter primaryLookupFailureCounter;
    private final Counter failoverActivatedCounter;
    private final Counter unavailableCounter;
    private final Clock clock;
    private volatile OffsetDateTime lastPrimaryLookupFailureAt;
    private volatile String lastPrimaryLookupFailureReason = "none";
    private volatile OffsetDateTime lastFailoverAt;
    private volatile String lastDecision = "startup";

    public RedirectRuntimeState(MeterRegistry meterRegistry) {
        this.cacheHitCounter = Counter.builder("link.redirect.cache.hit")
                .description("Number of redirect cache hits")
                .register(meterRegistry);
        this.cacheMissCounter = Counter.builder("link.redirect.cache.miss")
                .description("Number of redirect cache misses")
                .register(meterRegistry);
        this.primaryLookupFailureCounter = Counter.builder("link.redirect.primary.lookup.failure")
                .description("Number of redirect primary lookup failures")
                .register(meterRegistry);
        this.failoverActivatedCounter = Counter.builder("link.redirect.failover.activated")
                .description("Number of redirect failover activations")
                .register(meterRegistry);
        this.unavailableCounter = Counter.builder("link.redirect.unavailable")
                .description("Number of redirect requests that could not be served or failed over")
                .register(meterRegistry);
        this.clock = Clock.systemUTC();
    }

    public void recordCacheHit() {
        cacheHitCounter.increment();
        lastDecision = "cache-hit";
    }

    public void recordCacheMiss() {
        cacheMissCounter.increment();
        lastDecision = "cache-miss";
    }

    public void recordPrimaryLookupSuccess() {
        lastDecision = "primary-success";
    }

    public void recordPrimaryLookupFailure(String reason) {
        primaryLookupFailureCounter.increment();
        lastPrimaryLookupFailureAt = OffsetDateTime.now(clock);
        lastPrimaryLookupFailureReason = reason;
        lastDecision = "primary-failure";
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
}
