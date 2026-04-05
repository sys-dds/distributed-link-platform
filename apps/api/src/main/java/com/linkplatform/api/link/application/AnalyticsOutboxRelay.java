package com.linkplatform.api.link.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import com.linkplatform.api.runtime.ConditionalOnRuntimeModes;
import com.linkplatform.api.runtime.RuntimeMode;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnRuntimeModes({RuntimeMode.ALL, RuntimeMode.WORKER})
public class AnalyticsOutboxRelay {

    private final AnalyticsOutboxStore analyticsOutboxStore;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;
    private final String clickTopic;
    private final int batchSize;
    private final Duration leaseDuration;
    private final Duration retryBaseDelay;
    private final Duration retryMaxDelay;
    private final int maxAttempts;
    private final String workerId;
    private final Counter publishSuccessCounter;
    private final Counter publishFailureCounter;
    private final Counter retryCounter;
    private final Counter parkedCounter;

    @Autowired
    public AnalyticsOutboxRelay(
            AnalyticsOutboxStore analyticsOutboxStore,
            KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry meterRegistry,
            @Value("${link-platform.analytics.click-topic}") String clickTopic,
            @Value("${link-platform.analytics.outbox-relay-batch-size}") int batchSize,
            @Value("${link-platform.analytics.outbox-relay-lease-duration}") String leaseDuration,
            @Value("${link-platform.analytics.outbox-relay-retry-base-delay}") String retryBaseDelay,
            @Value("${link-platform.analytics.outbox-relay-retry-max-delay}") String retryMaxDelay,
            @Value("${link-platform.analytics.outbox-relay-max-attempts}") int maxAttempts) {
        this(
                analyticsOutboxStore,
                kafkaTemplate,
                meterRegistry,
                clickTopic,
                batchSize,
                Duration.parse(leaseDuration),
                Duration.parse(retryBaseDelay),
                Duration.parse(retryMaxDelay),
                maxAttempts,
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
            Duration retryBaseDelay,
            Duration retryMaxDelay,
            int maxAttempts,
            Clock clock,
            String workerId) {
        this.analyticsOutboxStore = analyticsOutboxStore;
        this.kafkaTemplate = kafkaTemplate;
        this.clickTopic = clickTopic;
        this.batchSize = batchSize;
        this.leaseDuration = leaseDuration;
        this.retryBaseDelay = retryBaseDelay;
        this.retryMaxDelay = retryMaxDelay;
        this.maxAttempts = maxAttempts;
        this.clock = clock;
        this.workerId = workerId;
        this.publishSuccessCounter = Counter.builder("link.analytics.outbox.publish.success")
                .description("Number of analytics outbox records published successfully")
                .register(meterRegistry);
        this.publishFailureCounter = Counter.builder("link.analytics.outbox.publish.failure")
                .description("Number of analytics outbox publish failures")
                .register(meterRegistry);
        this.retryCounter = Counter.builder("link.analytics.outbox.retry")
                .description("Number of analytics outbox delivery retries scheduled")
                .register(meterRegistry);
        this.parkedCounter = Counter.builder("link.analytics.outbox.parked.transition")
                .description("Number of analytics outbox records parked after repeated failures")
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
                handlePublishFailure(unpublishedRecord, now, exception);
            }
        }
    }

    private void handlePublishFailure(AnalyticsOutboxRecord unpublishedRecord, OffsetDateTime now, RuntimeException exception) {
        int attemptCount = unpublishedRecord.attemptCount() + 1;
        String errorSummary = compactErrorSummary(exception);
        if (attemptCount >= maxAttempts) {
            analyticsOutboxStore.recordPublishFailure(unpublishedRecord.id(), attemptCount, null, errorSummary, now);
            parkedCounter.increment();
            return;
        }

        analyticsOutboxStore.recordPublishFailure(
                unpublishedRecord.id(),
                attemptCount,
                now.plus(backoffForAttempt(attemptCount)),
                errorSummary,
                null);
        retryCounter.increment();
    }

    private Duration backoffForAttempt(int attemptCount) {
        Duration backoff = retryBaseDelay.multipliedBy(1L << Math.max(0, attemptCount - 1));
        return backoff.compareTo(retryMaxDelay) > 0 ? retryMaxDelay : backoff;
    }

    private String compactErrorSummary(RuntimeException exception) {
        Throwable root = exception;
        while (root.getCause() != null) {
            root = root.getCause();
        }

        String message = root.getMessage();
        String summary = root.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
        return summary.length() <= 255 ? summary : summary.substring(0, 255);
    }
}
