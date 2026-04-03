package com.linkplatform.api.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkplatform.api.link.application.AnalyticsOutboxRelay;
import com.linkplatform.api.link.application.AnalyticsOutboxStore;
import com.linkplatform.api.link.application.LinkStore;
import com.linkplatform.api.link.application.RedirectClickAnalyticsConsumer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class RuntimeModeConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    KafkaAutoConfiguration.class,
                    ServletWebServerFactoryAutoConfiguration.class))
            .withPropertyValues(
                    "spring.kafka.bootstrap-servers=localhost:9092",
                    "spring.kafka.consumer.group-id=runtime-mode-test",
                    "spring.kafka.listener.auto-startup=false",
                    "link-platform.analytics.click-topic=link-platform.analytics.redirect-clicks",
                    "link-platform.analytics.outbox-relay-batch-size=50",
                    "link-platform.analytics.outbox-relay-lease-duration=PT30S")
            .withUserConfiguration(TestConfiguration.class, AnalyticsOutboxRelay.class, RedirectClickAnalyticsConsumer.class);

    @Test
    void defaultCombinedModeKeepsAsyncComponentsEnabled() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AnalyticsOutboxRelay.class);
            assertThat(context).hasSingleBean(RedirectClickAnalyticsConsumer.class);

            TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
            context.getBean("workerModeWebServerCustomizer", WebServerFactoryCustomizer.class).customize(factory);
            assertThat(factory.getPort()).isEqualTo(8080);
        });
    }

    @Test
    void apiModeDisablesAsyncComponents() {
        contextRunner.withPropertyValues("link-platform.runtime.mode=api")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AnalyticsOutboxRelay.class);
                    assertThat(context).doesNotHaveBean(RedirectClickAnalyticsConsumer.class);

                    TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
                    context.getBean("workerModeWebServerCustomizer", WebServerFactoryCustomizer.class).customize(factory);
                    assertThat(factory.getPort()).isEqualTo(8080);
                });
    }

    @Test
    void workerModeEnablesAsyncComponentsAndDisablesPublicConnector() {
        contextRunner.withPropertyValues("link-platform.runtime.mode=worker")
                .run(context -> {
                    assertThat(context).hasSingleBean(AnalyticsOutboxRelay.class);
                    assertThat(context).hasSingleBean(RedirectClickAnalyticsConsumer.class);

                    TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
                    context.getBean("workerModeWebServerCustomizer", WebServerFactoryCustomizer.class).customize(factory);
                    assertThat(factory.getPort()).isEqualTo(-1);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration extends LinkPlatformRuntimeConfiguration {

        @Bean
        AnalyticsOutboxStore analyticsOutboxStore() {
            return new AnalyticsOutboxStore() {
                @Override
                public void saveRedirectClickEvent(com.linkplatform.api.link.application.RedirectClickAnalyticsEvent redirectClickAnalyticsEvent) {
                }

                @Override
                public long countUnpublished() {
                    return 0;
                }

                @Override
                public List<com.linkplatform.api.link.application.AnalyticsOutboxRecord> claimBatch(
                        String workerId,
                        OffsetDateTime now,
                        OffsetDateTime claimedUntil,
                        int limit) {
                    return List.of();
                }

                @Override
                public void markPublished(long id, OffsetDateTime publishedAt) {
                }
            };
        }

        @Bean
        LinkStore linkStore() {
            return Mockito.mock(LinkStore.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
