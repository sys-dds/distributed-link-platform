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
public class LinkLifecycleOutboxRelay {
    public static final String PIPELINE_NAME = "lifecycle";

    private final LinkLifecycleOutboxStore linkLifecycleOutboxStore;
    private final PipelineControlStore pipelineControlStore;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;
    private final String topic;
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
    public LinkLifecycleOutboxRelay(
            LinkLifecycleOutboxStore linkLifecycleOutboxStore,
            PipelineControlStore pipelineControlStore,
            KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry meterRegistry,
            @Value("${link-platform.lifecycle.topic}") String topic,
            @Value("${link-platform.lifecycle.outbox-relay-batch-size}") int batchSize,
            @Value("${link-platform.lifecycle.outbox-relay-lease-duration}") String leaseDuration,
            @Value("${link-platform.lifecycle.outbox-relay-retry-base-delay}") String retryBaseDelay,
            @Value("${link-platform.lifecycle.outbox-relay-retry-max-delay}") String retryMaxDelay,
            @Value("${link-platform.lifecycle.outbox-relay-max-attempts}") int maxAttempts) {
        this(
                linkLifecycleOutboxStore,
                pipelineControlStore,
                kafkaTemplate,
                meterRegistry,
                topic,
                batchSize,
                Duration.parse(leaseDuration),
                Duration.parse(retryBaseDelay),
                Duration.parse(retryMaxDelay),
                maxAttempts,
                Clock.systemUTC(),
                UUID.randomUUID().toString());
    }

    LinkLifecycleOutboxRelay(
            LinkLifecycleOutboxStore linkLifecycleOutboxStore,
            PipelineControlStore pipelineControlStore,
            KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry meterRegistry,
            String topic,
            int batchSize,
            Duration leaseDuration,
            Duration retryBaseDelay,
            Duration retryMaxDelay,
            int maxAttempts,
            Clock clock,
            String workerId) {
        this.linkLifecycleOutboxStore = linkLifecycleOutboxStore;
        this.pipelineControlStore = pipelineControlStore;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.batchSize = batchSize;
        this.leaseDuration = leaseDuration;
        this.retryBaseDelay = retryBaseDelay;
        this.retryMaxDelay = retryMaxDelay;
        this.maxAttempts = maxAttempts;
        this.clock = clock;
        this.workerId = workerId;
        this.publishSuccessCounter = Counter.builder("link.lifecycle.outbox.publish.success")
                .description("Number of lifecycle outbox records published successfully")
                .register(meterRegistry);
        this.publishFailureCounter = Counter.builder("link.lifecycle.outbox.publish.failure")
                .description("Number of lifecycle outbox publish failures")
                .register(meterRegistry);
        this.retryCounter = Counter.builder("link.lifecycle.outbox.retry")
                .description("Number of lifecycle outbox delivery retries scheduled")
                .register(meterRegistry);
        this.parkedCounter = Counter.builder("link.lifecycle.outbox.parked.transition")
                .description("Number of lifecycle outbox records parked after repeated failures")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${link-platform.lifecycle.outbox-relay-delay}")
    public void relayPendingEvents() {
        relayOnce();
    }

    public RelayIterationResult relayOnce() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        PipelineControl control = pipelineControlStore.get(PIPELINE_NAME);
        if (control.paused()) {
            return new RelayIterationResult(control.pipelineName(), true, 0, 0);
        }
        OffsetDateTime claimUntil = now.plus(leaseDuration);
        List<LinkLifecycleOutboxRecord> unpublishedRecords =
                linkLifecycleOutboxStore.claimBatch(workerId, now, claimUntil, batchSize);
        int processedCount = 0;
        int parkedCount = 0;

        for (LinkLifecycleOutboxRecord unpublishedRecord : unpublishedRecords) {
            try {
                kafkaTemplate.send(topic, unpublishedRecord.eventKey(), unpublishedRecord.payloadJson()).join();
                linkLifecycleOutboxStore.markPublished(unpublishedRecord.id(), OffsetDateTime.now(clock));
                publishSuccessCounter.increment();
                processedCount++;
            } catch (RuntimeException exception) {
                publishFailureCounter.increment();
                boolean parked = handlePublishFailure(unpublishedRecord, now, exception);
                if (parked) {
                    parkedCount++;
                }
                pipelineControlStore.recordRelayFailure(PIPELINE_NAME, OffsetDateTime.now(clock), compactErrorSummary(exception));
                throw exception;
            }
        }
        pipelineControlStore.recordRelaySuccess(PIPELINE_NAME, OffsetDateTime.now(clock));
        return new RelayIterationResult(PIPELINE_NAME, false, processedCount, parkedCount);
    }

    private boolean handlePublishFailure(
            LinkLifecycleOutboxRecord unpublishedRecord,
            OffsetDateTime now,
            RuntimeException exception) {
        int attemptCount = unpublishedRecord.attemptCount() + 1;
        String errorSummary = compactErrorSummary(exception);
        if (attemptCount >= maxAttempts) {
            linkLifecycleOutboxStore.recordPublishFailure(
                    unpublishedRecord.id(),
                    attemptCount,
                    null,
                    errorSummary,
                    now);
            parkedCounter.increment();
            return true;
        }

        linkLifecycleOutboxStore.recordPublishFailure(
                unpublishedRecord.id(),
                attemptCount,
                now.plus(backoffForAttempt(attemptCount)),
                errorSummary,
                null);
        retryCounter.increment();
        return false;
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

    public record RelayIterationResult(String pipelineName, boolean paused, int processedCount, int parkedCount) {
    }
}
