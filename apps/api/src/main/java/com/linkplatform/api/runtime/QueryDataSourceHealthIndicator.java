package com.linkplatform.api.runtime;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

public class QueryDataSourceHealthIndicator extends AbstractHealthIndicator {

    private final QueryRoutingDataSource queryRoutingDataSource;
    private final LinkPlatformRuntimeProperties runtimeProperties;

    public QueryDataSourceHealthIndicator(
            QueryRoutingDataSource queryRoutingDataSource,
            LinkPlatformRuntimeProperties runtimeProperties) {
        this.queryRoutingDataSource = queryRoutingDataSource;
        this.runtimeProperties = runtimeProperties;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        boolean required = runtimeProperties.getMode() == RuntimeMode.ALL
                || runtimeProperties.getMode() == RuntimeMode.CONTROL_PLANE_API;
        builder.up()
                .withDetail("required", required)
                .withDetail("replicaEnabled", queryRoutingDataSource.isQueryReplicaEnabled())
                .withDetail("lagSeconds", queryRoutingDataSource.getLastLagSeconds())
                .withDetail("dedicatedConfigured", queryRoutingDataSource.isDedicatedConfigured())
                .withDetail("usingPrimaryByDefault", queryRoutingDataSource.isUsingPrimaryByDefault())
                .withDetail("fallbackPolicy", "primary-on-dedicated-query-failure")
                .withDetail("route", queryRoutingDataSource.currentRoute())
                .withDetail("fallbackActive", queryRoutingDataSource.isFallbackActive())
                .withDetail("lastFallbackAt", queryRoutingDataSource.getLastFallbackAt())
                .withDetail("redirectRateLimitEnabled", runtimeProperties.getRedirect().getRateLimit().isEnabled());
        if (!required) {
            builder.withDetail("reason", "query reads are not served in this runtime mode");
            return;
        }
        if (!queryRoutingDataSource.isDedicatedConfigured()) {
            return;
        }
        boolean dedicatedAvailable = queryRoutingDataSource.isDedicatedAvailable();
        builder.withDetail("dedicatedAvailable", dedicatedAvailable);
        if (queryRoutingDataSource.getLastFallbackReason() != null) {
            builder.withDetail("lastFallbackReason", queryRoutingDataSource.getLastFallbackReason());
            builder.withDetail("lastFallbackAt", queryRoutingDataSource.getLastFallbackAt());
        }
    }

    public QueryRuntimeSnapshot snapshot() {
        return new QueryRuntimeSnapshot(
                queryRoutingDataSource.isDedicatedConfigured(),
                queryRoutingDataSource.isQueryReplicaEnabled(),
                queryRoutingDataSource.getLastLagSeconds(),
                queryRoutingDataSource.isFallbackActive(),
                queryRoutingDataSource.getLastFallbackAt(),
                queryRoutingDataSource.getLastFallbackReason());
    }

    public record QueryRuntimeSnapshot(
            boolean dedicatedQueryConfigured,
            boolean replicaEnabled,
            Long lagSeconds,
            boolean fallbackActive,
            java.time.OffsetDateTime lastFallbackAt,
            String lastFallbackReason) {
    }
}
