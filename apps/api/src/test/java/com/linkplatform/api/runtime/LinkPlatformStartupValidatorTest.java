package com.linkplatform.api.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class LinkPlatformStartupValidatorTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void partialDedicatedQueryDatasourceConfigurationFailsFast() {
        contextRunner
                .withPropertyValues("link-platform.query.datasource.username=query_user")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining(
                                "link-platform.query.datasource.url must be set when any dedicated query datasource property is configured");
                });
    }

    @Test
    void dedicatedQueryDatasourceRequiresUsernameWhenUrlIsConfigured() {
        contextRunner
                .withPropertyValues("link-platform.query.datasource.url=jdbc:h2:mem:query")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining(
                                    "link-platform.query.datasource.username must be set when link-platform.query.datasource.url is configured");
                });
    }

    @Test
    void emptyDedicatedQueryDatasourceConfigurationStartsNormally() {
        contextRunner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void redirectEnabledRuntimeRequiresNonBlankRegion() {
        contextRunner.withPropertyValues("link-platform.runtime.redirect.region= ")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining(
                                    "link-platform.runtime.redirect.region must be set when the redirect surface is enabled");
                });
    }

    @Test
    void failoverRegionMustDifferFromPrimaryRegion() {
        contextRunner.withPropertyValues(
                        "link-platform.runtime.redirect.region=eu-west-1",
                        "link-platform.runtime.redirect.failover-region=eu-west-1")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining(
                                    "link-platform.runtime.redirect.failover-region must differ from link-platform.runtime.redirect.region");
                });
    }

    @Test
    void failoverRegionRequiresCacheEnabled() {
        contextRunner.withPropertyValues(
                        "link-platform.runtime.redirect.region=eu-west-1",
                        "link-platform.runtime.redirect.failover-region=us-east-1",
                        "link-platform.runtime.redirect.failover-base-url=http://localhost:8082",
                        "link-platform.cache.enabled=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining(
                                    "link-platform.cache.enabled must remain true when link-platform.runtime.redirect.failover-region is configured");
                });
    }

    @Test
    void dedicatedQueryDatasourceIsRejectedForRedirectRuntime() {
        contextRunner.withPropertyValues(
                        "link-platform.runtime.mode=redirect",
                        "link-platform.query.datasource.url=jdbc:h2:mem:query",
                        "link-platform.query.datasource.username=query_user")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining(
                                    "Dedicated query datasource configuration is only valid for all or control-plane-api runtime modes");
                });
    }

    @Test
    void redirectRuntimeRequiresCacheToStayEnabled() {
        contextRunner.withPropertyValues(
                        "link-platform.runtime.mode=redirect",
                        "link-platform.cache.enabled=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining(
                                    "link-platform.cache.enabled must remain true when link-platform.runtime.mode is redirect");
                });
    }

    @Test
    void failoverRegionAndBaseUrlMustBeConfiguredTogether() {
        contextRunner.withPropertyValues("link-platform.runtime.redirect.failover-region=us-east-1")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining(
                                    "link-platform.runtime.redirect.failover-region and link-platform.runtime.redirect.failover-base-url must be configured together");
                });
    }

    @Test
    void failoverBaseUrlMustDifferFromPublicBaseUrl() {
        contextRunner.withPropertyValues(
                        "link-platform.runtime.redirect.failover-region=us-east-1",
                        "link-platform.runtime.redirect.failover-base-url=http://localhost:8080")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining(
                                    "link-platform.runtime.redirect.failover-base-url must differ from link-platform.public-base-url");
                });
    }

    @Test
    void failoverBaseUrlMustBeAbsoluteHttpUrl() {
        contextRunner.withPropertyValues(
                        "link-platform.runtime.redirect.failover-region=us-east-1",
                        "link-platform.runtime.redirect.failover-base-url=/relative")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining(
                                    "link-platform.runtime.redirect.failover-base-url must be an absolute http/https URL");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @org.springframework.boot.context.properties.EnableConfigurationProperties({
            LinkPlatformQueryProperties.class,
            LinkPlatformRuntimeProperties.class
    })
    static class TestConfiguration {

        @Bean
        LinkPlatformStartupValidator linkPlatformStartupValidator(
                LinkPlatformQueryProperties queryProperties,
                LinkPlatformRuntimeProperties runtimeProperties,
                @org.springframework.beans.factory.annotation.Value("${link-platform.cache.enabled:true}") boolean cacheEnabled,
                @org.springframework.beans.factory.annotation.Value("${link-platform.public-base-url:http://localhost:8080}") String publicBaseUrl) {
            return new LinkPlatformStartupValidator(queryProperties, runtimeProperties, cacheEnabled, publicBaseUrl);
        }
    }
}
