package com.linkplatform.api.runtime;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

public class RuntimeRoleHealthIndicator extends AbstractHealthIndicator {

    private final LinkPlatformRuntimeProperties runtimeProperties;

    public RuntimeRoleHealthIndicator(LinkPlatformRuntimeProperties runtimeProperties) {
        this.runtimeProperties = runtimeProperties;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        RuntimeMode mode = runtimeProperties.getMode();
        builder.up()
                .withDetail("mode", mode.name())
                .withDetail("httpEnabled", runtimeProperties.httpEnabled())
                .withDetail("shouldServeHttp", runtimeProperties.httpEnabled())
                .withDetail("redirectEnabled", runtimeProperties.redirectEnabled())
                .withDetail("controlPlaneEnabled", runtimeProperties.controlPlaneEnabled())
                .withDetail("workerEnabled", runtimeProperties.workerEnabled())
                .withDetail("redirectSurfaceExposed", runtimeProperties.redirectEnabled() && runtimeProperties.httpEnabled())
                .withDetail("controlPlaneSurfaceExposed", runtimeProperties.controlPlaneEnabled() && runtimeProperties.httpEnabled())
                .withDetail("workerSurfaceExposed", runtimeProperties.workerEnabled() && runtimeProperties.httpEnabled());
    }
}
