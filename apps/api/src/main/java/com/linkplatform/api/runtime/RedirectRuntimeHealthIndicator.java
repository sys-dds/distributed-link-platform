package com.linkplatform.api.runtime;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

public class RedirectRuntimeHealthIndicator extends AbstractHealthIndicator {

    private final LinkPlatformRuntimeProperties runtimeProperties;
    private final boolean cacheEnabled;

    public RedirectRuntimeHealthIndicator(LinkPlatformRuntimeProperties runtimeProperties, boolean cacheEnabled) {
        this.runtimeProperties = runtimeProperties;
        this.cacheEnabled = cacheEnabled;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        RuntimeMode mode = runtimeProperties.getMode();
        boolean redirectEnabled = mode == RuntimeMode.ALL || mode == RuntimeMode.REDIRECT;
        builder.up()
                .withDetail("required", redirectEnabled)
                .withDetail("region", runtimeProperties.getRedirect().getRegion())
                .withDetail("cacheEnabled", cacheEnabled)
                .withDetail("routeStrategy", "cache-first-primary-lookup")
                .withDetail("analyticsWriteMode", "durable-outbox");
        if (!redirectEnabled) {
            builder.withDetail("reason", "redirect surface is disabled in this runtime mode");
            return;
        }
        String failoverRegion = runtimeProperties.getRedirect().getFailoverRegion();
        builder.withDetail("failoverConfigured", failoverRegion != null)
                .withDetail("failoverMode", failoverRegion == null ? "single-region" : "regional-fallback");
        if (failoverRegion != null) {
            builder.withDetail("failoverRegion", failoverRegion)
                    .withDetail("failoverReady", cacheEnabled);
        }
    }
}
