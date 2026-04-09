package com.linkplatform.api.runtime;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LinkPlatformRuntimeProperties.class)
public class LinkPlatformRuntimeConfiguration {

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> runtimeModeWebServerCustomizer(
            LinkPlatformRuntimeProperties runtimeProperties) {
        return factory -> {
            if (!runtimeProperties.getMode().webServerEnabled()) {
                factory.setPort(-1);
            }
        };
    }

    @Bean(name = "runtimeRoleHealthIndicator")
    RuntimeRoleHealthIndicator runtimeRoleHealthIndicator(LinkPlatformRuntimeProperties runtimeProperties) {
        return new RuntimeRoleHealthIndicator(runtimeProperties);
    }

    @Bean(name = "redirectRuntimeHealthIndicator")
    RedirectRuntimeHealthIndicator redirectRuntimeHealthIndicator(
            LinkPlatformRuntimeProperties runtimeProperties,
            @org.springframework.beans.factory.annotation.Value("${link-platform.cache.enabled:true}") boolean cacheEnabled,
            @org.springframework.beans.factory.annotation.Value("${link-platform.public-base-url}") String publicBaseUrl,
            RedirectRuntimeState redirectRuntimeState,
            com.linkplatform.api.link.application.RedirectRateLimitService redirectRateLimitService) {
        return new RedirectRuntimeHealthIndicator(
                runtimeProperties,
                cacheEnabled,
                publicBaseUrl,
                redirectRuntimeState,
                redirectRateLimitService);
    }

    @Bean
    RedirectRuntimeState redirectRuntimeState(io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        return new RedirectRuntimeState(meterRegistry);
    }
}
