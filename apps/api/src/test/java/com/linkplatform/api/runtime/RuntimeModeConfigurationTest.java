package com.linkplatform.api.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkplatform.api.link.api.AnalyticsPipelineController;
import com.linkplatform.api.link.api.LifecyclePipelineController;
import com.linkplatform.api.link.api.LinkAnalyticsController;
import com.linkplatform.api.link.api.LinkController;
import com.linkplatform.api.link.api.LinkRedirectController;
import com.linkplatform.api.link.application.AnalyticsOutboxRelay;
import com.linkplatform.api.link.application.AnalyticsOutboxStore;
import com.linkplatform.api.link.application.LinkApplicationService;
import com.linkplatform.api.link.application.LinkReadCache;
import com.linkplatform.api.link.application.LinkLifecycleConsumer;
import com.linkplatform.api.link.application.LinkLifecycleOutboxRelay;
import com.linkplatform.api.link.application.LinkLifecycleOutboxStore;
import com.linkplatform.api.link.application.LinkStore;
import com.linkplatform.api.link.application.RedirectClickAnalyticsConsumer;
import com.linkplatform.api.owner.api.MeController;
import com.linkplatform.api.owner.application.OwnerAccessService;
import com.linkplatform.api.projection.ProjectionJobRunner;
import com.linkplatform.api.projection.ProjectionJobsController;
import com.linkplatform.api.projection.ProjectionJobService;
import com.linkplatform.api.projection.ProjectionJobStore;
import com.linkplatform.api.system.api.SystemPingController;
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
                    "link-platform.analytics.outbox-relay-lease-duration=PT30S",
                    "link-platform.analytics.outbox-relay-retry-base-delay=PT5S",
                    "link-platform.analytics.outbox-relay-retry-max-delay=PT5M",
                    "link-platform.analytics.outbox-relay-max-attempts=5",
                    "link-platform.lifecycle.topic=link-platform.lifecycle.link-events",
                    "link-platform.lifecycle.outbox-relay-batch-size=50",
                    "link-platform.lifecycle.outbox-relay-lease-duration=PT30S",
                    "link-platform.lifecycle.outbox-relay-retry-base-delay=PT5S",
                    "link-platform.lifecycle.outbox-relay-retry-max-delay=PT5M",
                    "link-platform.lifecycle.outbox-relay-max-attempts=5",
                    "link-platform.projection-jobs.runner-delay=5000",
                    "link-platform.projection-jobs.lease-duration=PT30S",
                    "link-platform.projection-jobs.chunk-size=100")
            .withUserConfiguration(
                    TestConfiguration.class,
                    LinkRedirectController.class,
                    LinkController.class,
                    LinkAnalyticsController.class,
                    MeController.class,
                    SystemPingController.class,
                    AnalyticsPipelineController.class,
                    LifecyclePipelineController.class,
                    ProjectionJobsController.class,
                    AnalyticsOutboxRelay.class,
                    RedirectClickAnalyticsConsumer.class,
                    LinkLifecycleOutboxRelay.class,
                    LinkLifecycleConsumer.class,
                    ProjectionJobService.class,
                    ProjectionJobRunner.class);

    @Test
    void defaultCombinedModeKeepsAsyncComponentsEnabled() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(LinkRedirectController.class);
            assertThat(context).hasSingleBean(LinkController.class);
            assertThat(context).hasSingleBean(LinkAnalyticsController.class);
            assertThat(context).hasSingleBean(MeController.class);
            assertThat(context).hasSingleBean(SystemPingController.class);
            assertThat(context).hasSingleBean(AnalyticsPipelineController.class);
            assertThat(context).hasSingleBean(LifecyclePipelineController.class);
            assertThat(context).hasSingleBean(ProjectionJobsController.class);
            assertThat(context).hasSingleBean(AnalyticsOutboxRelay.class);
            assertThat(context).hasSingleBean(RedirectClickAnalyticsConsumer.class);
            assertThat(context).hasSingleBean(LinkLifecycleOutboxRelay.class);
            assertThat(context).hasSingleBean(LinkLifecycleConsumer.class);
            assertThat(context).hasSingleBean(ProjectionJobRunner.class);

            TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
            context.getBean("runtimeModeWebServerCustomizer", WebServerFactoryCustomizer.class).customize(factory);
            assertThat(factory.getPort()).isEqualTo(8080);
        });
    }

    @Test
    void controlPlaneApiModeKeepsOwnerSurfaceAndDisablesWorkerComponents() {
        contextRunner.withPropertyValues("link-platform.runtime.mode=control-plane-api")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(LinkRedirectController.class);
                    assertThat(context).hasSingleBean(LinkController.class);
                    assertThat(context).hasSingleBean(LinkAnalyticsController.class);
                    assertThat(context).hasSingleBean(MeController.class);
                    assertThat(context).hasSingleBean(SystemPingController.class);
                    assertThat(context).hasSingleBean(AnalyticsPipelineController.class);
                    assertThat(context).hasSingleBean(LifecyclePipelineController.class);
                    assertThat(context).hasSingleBean(ProjectionJobsController.class);
                    assertThat(context).doesNotHaveBean(AnalyticsOutboxRelay.class);
                    assertThat(context).doesNotHaveBean(RedirectClickAnalyticsConsumer.class);
                    assertThat(context).doesNotHaveBean(LinkLifecycleOutboxRelay.class);
                    assertThat(context).doesNotHaveBean(LinkLifecycleConsumer.class);
                    assertThat(context).doesNotHaveBean(ProjectionJobRunner.class);

                    TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
                    context.getBean("runtimeModeWebServerCustomizer", WebServerFactoryCustomizer.class).customize(factory);
                    assertThat(factory.getPort()).isEqualTo(8080);
                });
    }

    @Test
    void redirectModeServesOnlyPublicRedirectSurface() {
        contextRunner.withPropertyValues("link-platform.runtime.mode=redirect")
                .run(context -> {
                    assertThat(context).hasSingleBean(LinkRedirectController.class);
                    assertThat(context).doesNotHaveBean(LinkController.class);
                    assertThat(context).doesNotHaveBean(LinkAnalyticsController.class);
                    assertThat(context).doesNotHaveBean(MeController.class);
                    assertThat(context).doesNotHaveBean(SystemPingController.class);
                    assertThat(context).doesNotHaveBean(AnalyticsPipelineController.class);
                    assertThat(context).doesNotHaveBean(LifecyclePipelineController.class);
                    assertThat(context).doesNotHaveBean(ProjectionJobsController.class);
                    assertThat(context).doesNotHaveBean(AnalyticsOutboxRelay.class);
                    assertThat(context).doesNotHaveBean(RedirectClickAnalyticsConsumer.class);
                    assertThat(context).doesNotHaveBean(LinkLifecycleOutboxRelay.class);
                    assertThat(context).doesNotHaveBean(LinkLifecycleConsumer.class);
                    assertThat(context).doesNotHaveBean(ProjectionJobRunner.class);

                    TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
                    context.getBean("runtimeModeWebServerCustomizer", WebServerFactoryCustomizer.class).customize(factory);
                    assertThat(factory.getPort()).isEqualTo(8080);
                });
    }

    @Test
    void workerModeEnablesAsyncComponentsAndDisablesPublicConnector() {
        contextRunner.withPropertyValues("link-platform.runtime.mode=worker")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(LinkRedirectController.class);
                    assertThat(context).doesNotHaveBean(LinkController.class);
                    assertThat(context).doesNotHaveBean(LinkAnalyticsController.class);
                    assertThat(context).doesNotHaveBean(MeController.class);
                    assertThat(context).doesNotHaveBean(SystemPingController.class);
                    assertThat(context).doesNotHaveBean(AnalyticsPipelineController.class);
                    assertThat(context).doesNotHaveBean(LifecyclePipelineController.class);
                    assertThat(context).doesNotHaveBean(ProjectionJobsController.class);
                    assertThat(context).hasSingleBean(AnalyticsOutboxRelay.class);
                    assertThat(context).hasSingleBean(RedirectClickAnalyticsConsumer.class);
                    assertThat(context).hasSingleBean(LinkLifecycleOutboxRelay.class);
                    assertThat(context).hasSingleBean(LinkLifecycleConsumer.class);
                    assertThat(context).hasSingleBean(ProjectionJobRunner.class);

                    TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
                    context.getBean("runtimeModeWebServerCustomizer", WebServerFactoryCustomizer.class).customize(factory);
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
                public long countEligible(OffsetDateTime now) {
                    return 0;
                }

                @Override
                public long countParked() {
                    return 0;
                }

                @Override
                public Double findOldestEligibleAgeSeconds(OffsetDateTime now) {
                    return null;
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

                @Override
                public void recordPublishFailure(
                        long id,
                        int attemptCount,
                        OffsetDateTime nextAttemptAt,
                        String lastErrorSummary,
                        OffsetDateTime parkedAt) {
                }

                @Override
                public List<com.linkplatform.api.link.application.AnalyticsOutboxRecord> findParked(int limit) {
                    return List.of();
                }

                @Override
                public boolean requeueParked(long id, OffsetDateTime nextAttemptAt) {
                    return false;
                }
            };
        }

        @Bean
        LinkLifecycleOutboxStore linkLifecycleOutboxStore() {
            return new LinkLifecycleOutboxStore() {
                @Override
                public void saveLinkLifecycleEvent(com.linkplatform.api.link.application.LinkLifecycleEvent linkLifecycleEvent) {
                }

                @Override
                public long countUnpublished() {
                    return 0;
                }

                @Override
                public long countEligible(OffsetDateTime now) {
                    return 0;
                }

                @Override
                public long countParked() {
                    return 0;
                }

                @Override
                public Double findOldestEligibleAgeSeconds(OffsetDateTime now) {
                    return null;
                }

                @Override
                public List<com.linkplatform.api.link.application.LinkLifecycleOutboxRecord> claimBatch(
                        String workerId,
                        OffsetDateTime now,
                        OffsetDateTime claimedUntil,
                        int limit) {
                    return List.of();
                }

                @Override
                public void markPublished(long id, OffsetDateTime publishedAt) {
                }

                @Override
                public void recordPublishFailure(
                        long id,
                        int attemptCount,
                        OffsetDateTime nextAttemptAt,
                        String lastErrorSummary,
                        OffsetDateTime parkedAt) {
                }

                @Override
                public List<com.linkplatform.api.link.application.LinkLifecycleOutboxRecord> findParked(int limit) {
                    return List.of();
                }

                @Override
                public boolean requeueParked(long id, OffsetDateTime nextAttemptAt) {
                    return false;
                }

                @Override
                public List<com.linkplatform.api.link.application.LinkLifecycleEvent> findAllHistory() {
                    return List.of();
                }

                @Override
                public List<com.linkplatform.api.link.application.LinkLifecycleHistoryRecord> findHistoryChunkAfter(long afterId, int limit) {
                    return List.of();
                }
            };
        }

        @Bean
        LinkStore linkStore() {
            return Mockito.mock(LinkStore.class);
        }

        @Bean
        LinkReadCache linkReadCache() {
            return Mockito.mock(LinkReadCache.class);
        }

        @Bean
        LinkApplicationService linkApplicationService() {
            return Mockito.mock(LinkApplicationService.class);
        }

        @Bean
        OwnerAccessService ownerAccessService() {
            return Mockito.mock(OwnerAccessService.class);
        }

        @Bean
        ProjectionJobStore projectionJobStore() {
            return Mockito.mock(ProjectionJobStore.class);
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
