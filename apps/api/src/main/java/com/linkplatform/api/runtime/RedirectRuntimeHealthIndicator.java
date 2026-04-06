package com.linkplatform.api.runtime;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

public class RedirectRuntimeHealthIndicator extends AbstractHealthIndicator {

    private final LinkPlatformRuntimeProperties runtimeProperties;
    private final boolean cacheEnabled;
    private final String publicBaseUrl;
    private final RedirectRuntimeState redirectRuntimeState;

    public RedirectRuntimeHealthIndicator(
            LinkPlatformRuntimeProperties runtimeProperties,
            boolean cacheEnabled,
            String publicBaseUrl,
            RedirectRuntimeState redirectRuntimeState) {
        this.runtimeProperties = runtimeProperties;
        this.cacheEnabled = cacheEnabled;
        this.publicBaseUrl = publicBaseUrl;
        this.redirectRuntimeState = redirectRuntimeState;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        RuntimeMode mode = runtimeProperties.getMode();
        boolean redirectEnabled = mode == RuntimeMode.ALL || mode == RuntimeMode.REDIRECT;
        builder.up()
                .withDetail("required", redirectEnabled)
                .withDetail("region", runtimeProperties.getRedirect().getRegion())
                .withDetail("publicBaseUrl", publicBaseUrl)
                .withDetail("cacheEnabled", cacheEnabled)
                .withDetail("routeStrategy", "cache-first-primary-lookup")
                .withDetail("cacheDegradationPolicy", "fallback-to-primary")
                .withDetail("analyticsWriteMode", "durable-outbox")
                .withDetail("lastDecision", redirectRuntimeState.getLastDecision());
        if (!redirectEnabled) {
            builder.withDetail("reason", "redirect surface is disabled in this runtime mode");
            return;
        }
        String failoverRegion = runtimeProperties.getRedirect().getFailoverRegion();
        String failoverBaseUrl = runtimeProperties.getRedirect().getFailoverBaseUrl();
        builder.withDetail("failoverConfigured", failoverRegion != null)
                .withDetail("failoverMode", failoverRegion == null ? "single-region" : "regional-fallback")
                .withDetail(
                        "primaryFailurePolicy",
                        failoverRegion == null ? "fail-closed-service-unavailable" : "regional-failover");
        if (failoverRegion != null) {
            builder.withDetail("failoverRegion", failoverRegion)
                    .withDetail("failoverBaseUrl", failoverBaseUrl)
                    .withDetail("failoverReady", cacheEnabled);
        }
        if (redirectRuntimeState.getLastPrimaryLookupFailureAt() != null) {
            builder.withDetail("lastPrimaryLookupFailureAt", redirectRuntimeState.getLastPrimaryLookupFailureAt())
                    .withDetail("lastPrimaryLookupFailureReason", redirectRuntimeState.getLastPrimaryLookupFailureReason());
        }
        if (redirectRuntimeState.getLastFailoverAt() != null) {
            builder.withDetail("lastFailoverAt", redirectRuntimeState.getLastFailoverAt());
        }
    }
}
