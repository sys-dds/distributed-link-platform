package com.linkplatform.api.link.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${link-platform.runtime.mode:all}' == 'all' or '${link-platform.runtime.mode:all}' == 'worker'")
public class AnalyticsOutboxRelay {

    private final AnalyticsOutboxStore analyticsOutboxStore;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;
    private final String clickTopic;
    private final int batchSize;
    private final Duration leaseDuration;
    private final String workerId;
    private final Counter publishSuccessCounter;
    private final Counter publishFailureCounter;

    @Autowired
    public AnalyticsOutboxRelay(
            AnalyticsOutboxStore analyticsOutboxStore,
            KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry meterRegistry,
            @Value("${link-platform.analytics.click-topic}") String clickTopic,
            @Value("${link-platform.analytics.outbox-relay-batch-size}") int batchSize,
            @Value("${link-platform.analytics.outbox-relay-lease-duration}") String leaseDuration) {
        this(
                analyticsOutboxStore,
                kafkaTemplate,
                meterRegistry,
                clickTopic,
                batchSize,
                Duration.parse(leaseDuration),
                Clock.systemUTC(),
                UUID.randomUUID().toString());
    }

    AnalyticsOutboxRelay(
            AnalyticsOutboxStore analyticsOutboxStore,
            KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry meterRegistry,
            String clickTopic,
            int batchSize,
            Duration leaseDuration,
            Clock clock,
            String workerId) {
        this.analyticsOutboxStore = analyticsOutboxStore;
        this.kafkaTemplate = kafkaTemplate;
        this.clickTopic = clickTopic;
        this.batchSize = batchSize;
        this.leaseDuration = leaseDuration;
        this.clock = clock;
        this.workerId = workerId;
        this.publishSuccessCounter = Counter.builder("link.analytics.outbox.publish.success")
                .description("Number of analytics outbox records published successfully")
                .register(meterRegistry);
        this.publishFailureCounter = Counter.builder("link.analytics.outbox.publish.failure")
                .description("Number of analytics outbox publish failures")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${link-platform.analytics.outbox-relay-delay}")
    public void relayPendingEvents() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime claimUntil = now.plus(leaseDuration);
        List<AnalyticsOutboxRecord> unpublishedRecords = analyticsOutboxStore.claimBatch(workerId, now, claimUntil, batchSize);

        for (AnalyticsOutboxRecord unpublishedRecord : unpublishedRecords) {
            try {
                kafkaTemplate.send(clickTopic, unpublishedRecord.eventKey(), unpublishedRecord.payloadJson()).join();
                analyticsOutboxStore.markPublished(unpublishedRecord.id(), OffsetDateTime.now(clock));
                publishSuccessCounter.increment();
            } catch (RuntimeException exception) {
                publishFailureCounter.increment();
                throw exception;
            }
        }
    }
}
