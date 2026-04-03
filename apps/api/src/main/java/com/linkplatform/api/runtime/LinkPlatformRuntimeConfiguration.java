package com.linkplatform.api.runtime;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LinkPlatformRuntimeProperties.class)
public class LinkPlatformRuntimeConfiguration {

    @Bean
    WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> workerModeWebServerCustomizer(
            LinkPlatformRuntimeProperties runtimeProperties) {
        return factory -> {
            if (runtimeProperties.getMode() == RuntimeMode.WORKER) {
                factory.setPort(-1);
            }
        };
    }
}
