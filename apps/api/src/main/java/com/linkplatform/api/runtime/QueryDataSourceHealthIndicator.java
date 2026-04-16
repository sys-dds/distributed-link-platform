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
        Long lagSeconds = queryRoutingDataSource.getLastLagSeconds();
        Long heartbeatAgeSeconds = queryRoutingDataSource.getLastHeartbeatAgeSeconds();
        builder.up()
                .withDetail("required", required)
                .withDetail("replicaEnabled", queryRoutingDataSource.isQueryReplicaEnabled())
                .withDetail("dedicatedConfigured", queryRoutingDataSource.isDedicatedConfigured())
                .withDetail("usingPrimaryByDefault", queryRoutingDataSource.isUsingPrimaryByDefault())
                .withDetail("fallbackPolicy", "primary-on-dedicated-query-failure")
                .withDetail("route", queryRoutingDataSource.currentRoute())
                .withDetail("fallbackActive", queryRoutingDataSource.isFallbackActive())
                .withDetail("redirectRateLimitEnabled", runtimeProperties.getRedirect().getRateLimit().isEnabled());
        withNullableDetail(builder, "lagSeconds", lagSeconds);
        withNullableDetail(builder, "heartbeatAgeSeconds", heartbeatAgeSeconds);
        withNullableDetail(builder, "lastFallbackAt", queryRoutingDataSource.getLastFallbackAt());
        withNullableDetail(builder, "lastFallbackReason", queryRoutingDataSource.getLastFallbackReason());
        if (!required) {
            builder.withDetail("reason", "query reads are not served in this runtime mode");
            return;
        }
        if (!queryRoutingDataSource.isDedicatedConfigured()) {
            return;
        }
        boolean dedicatedAvailable = queryRoutingDataSource.isDedicatedAvailable();
        builder.withDetail("dedicatedAvailable", dedicatedAvailable);
    }

    private void withNullableDetail(Health.Builder builder, String name, Object value) {
        if (value != null) {
            builder.withDetail(name, value);
        }
    }

    public QueryRuntimeSnapshot snapshot() {
        return new QueryRuntimeSnapshot(
                queryRoutingDataSource.isDedicatedConfigured(),
                queryRoutingDataSource.isQueryReplicaEnabled(),
                queryRoutingDataSource.getLastLagSeconds(),
                queryRoutingDataSource.getLastHeartbeatAgeSeconds(),
                queryRoutingDataSource.isFallbackActive(),
                queryRoutingDataSource.getLastFallbackAt(),
                queryRoutingDataSource.getLastFallbackReason());
    }

    public record QueryRuntimeSnapshot(
            boolean dedicatedQueryConfigured,
            boolean replicaEnabled,
            Long lagSeconds,
            Long heartbeatAgeSeconds,
            boolean fallbackActive,
            java.time.OffsetDateTime lastFallbackAt,
            String lastFallbackReason) {
    }
}
