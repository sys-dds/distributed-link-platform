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
                .withDetail("httpEnabled", mode.webServerEnabled())
                .withDetail("redirectEnabled", mode == RuntimeMode.ALL || mode == RuntimeMode.REDIRECT)
                .withDetail("controlPlaneEnabled", mode == RuntimeMode.ALL || mode == RuntimeMode.CONTROL_PLANE_API)
                .withDetail("workerEnabled", mode == RuntimeMode.ALL || mode == RuntimeMode.WORKER);
    }
}
