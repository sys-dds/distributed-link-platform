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
    void emptyDedicatedQueryDatasourceConfigurationStartsNormally() {
        contextRunner.run(context -> assertThat(context).hasNotFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @org.springframework.boot.context.properties.EnableConfigurationProperties(LinkPlatformQueryProperties.class)
    static class TestConfiguration {

        @Bean
        LinkPlatformStartupValidator linkPlatformStartupValidator(LinkPlatformQueryProperties queryProperties) {
            return new LinkPlatformStartupValidator(queryProperties);
        }
    }
}
