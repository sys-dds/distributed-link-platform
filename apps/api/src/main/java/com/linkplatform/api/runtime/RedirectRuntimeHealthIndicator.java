package com.linkplatform.api.runtime;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

public class RedirectRuntimeHealthIndicator extends AbstractHealthIndicator {

    private final LinkPlatformRuntimeProperties runtimeProperties;
    private final boolean cacheEnabled;
    private final String publicBaseUrl;
    private final RedirectRuntimeState redirectRuntimeState;
    private final com.linkplatform.api.link.application.RedirectRateLimitService redirectRateLimitService;

    public RedirectRuntimeHealthIndicator(
            LinkPlatformRuntimeProperties runtimeProperties,
            boolean cacheEnabled,
            String publicBaseUrl,
            RedirectRuntimeState redirectRuntimeState,
            com.linkplatform.api.link.application.RedirectRateLimitService redirectRateLimitService) {
        this.runtimeProperties = runtimeProperties;
        this.cacheEnabled = cacheEnabled;
        this.publicBaseUrl = publicBaseUrl;
        this.redirectRuntimeState = redirectRuntimeState;
        this.redirectRateLimitService = redirectRateLimitService;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        boolean redirectEnabled = runtimeProperties.redirectEnabled();
        boolean failoverConfigured = runtimeProperties.getRedirect().failoverConfigured();
        builder.up()
                .withDetail("required", redirectEnabled)
                .withDetail("region", runtimeProperties.getRedirect().getRegion())
                .withDetail("publicBaseUrl", publicBaseUrl)
                .withDetail("cacheEnabled", cacheEnabled)
                .withDetail("cacheRequired", redirectEnabled)
                .withDetail("failoverConfigured", failoverConfigured)
                .withDetail("redirectRateLimitEnabled", runtimeProperties.getRedirect().getRateLimit().isEnabled())
                .withDetail("redirectRateLimitLastStoreMode", redirectRateLimitService.getLastStoreMode())
                .withDetail("lastDecision", redirectRuntimeState.getLastDecision())
                .withDetail("lastDegradedPath", redirectRuntimeState.getLastDegradedPath());
        if (!redirectEnabled) {
            builder.withDetail("reason", "redirect surface is disabled in this runtime mode");
            return;
        }
        builder.withDetail("runtimeState", runtimeStateLabel(failoverConfigured))
                .withDetail("primaryFailurePolicy", failoverConfigured ? "regional-failover" : "fail-closed-service-unavailable");
        if (failoverConfigured) {
            builder.withDetail("failoverRegion", runtimeProperties.getRedirect().getFailoverRegion())
                    .withDetail("failoverBaseUrl", runtimeProperties.getRedirect().getFailoverBaseUrl());
        }
        if (redirectRuntimeState.getLastPrimaryLookupFailureAt() != null) {
            builder.withDetail("lastPrimaryLookupFailureAt", redirectRuntimeState.getLastPrimaryLookupFailureAt())
                    .withDetail("lastPrimaryLookupFailureReason", redirectRuntimeState.getLastPrimaryLookupFailureReason());
        }
        if (redirectRuntimeState.getLastFailoverAt() != null) {
            builder.withDetail("lastFailoverAt", redirectRuntimeState.getLastFailoverAt());
        }
        if (redirectRuntimeState.getLastDegradedAt() != null) {
            builder.withDetail("lastDegradedAt", redirectRuntimeState.getLastDegradedAt());
        }
    }

    private String runtimeStateLabel(boolean failoverConfigured) {
        if ("cache-bypass".equals(redirectRuntimeState.getLastDegradedPath())
                || "cache-degraded".equals(redirectRuntimeState.getLastDegradedPath())) {
            return "degraded-cache-primary-only";
        }
        if (failoverConfigured) {
            return "failover-ready";
        }
        return "single-region";
    }
}
