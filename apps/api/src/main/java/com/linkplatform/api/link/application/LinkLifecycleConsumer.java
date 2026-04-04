package com.linkplatform.api.link.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${link-platform.runtime.mode:all}' == 'all' or '${link-platform.runtime.mode:all}' == 'worker'")
public class LinkLifecycleConsumer {

    private final ObjectMapper objectMapper;
    private final LinkStore linkStore;
    private final Counter processedCounter;
    private final Counter duplicateCounter;

    public LinkLifecycleConsumer(ObjectMapper objectMapper, LinkStore linkStore, MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.linkStore = linkStore;
        this.processedCounter = Counter.builder("link.lifecycle.consumer.processed")
                .description("Number of lifecycle events processed")
                .register(meterRegistry);
        this.duplicateCounter = Counter.builder("link.lifecycle.consumer.duplicate")
                .description("Number of duplicate lifecycle events ignored")
                .register(meterRegistry);
    }

    @KafkaListener(topics = "${link-platform.lifecycle.topic}")
    public void consume(String payloadJson) {
        LinkLifecycleEvent linkLifecycleEvent = deserialize(payloadJson);
        boolean persisted = linkStore.recordActivityIfAbsent(
                linkLifecycleEvent.eventId(),
                toActivityEvent(linkLifecycleEvent));
        if (persisted) {
            processedCounter.increment();
            return;
        }
        duplicateCounter.increment();
    }

    private LinkActivityEvent toActivityEvent(LinkLifecycleEvent linkLifecycleEvent) {
        LinkActivityType activityType = switch (linkLifecycleEvent.eventType()) {
            case CREATED -> LinkActivityType.CREATED;
            case UPDATED, EXPIRATION_UPDATED -> LinkActivityType.UPDATED;
            case DELETED -> LinkActivityType.DELETED;
        };
        return new LinkActivityEvent(
                activityType,
                linkLifecycleEvent.slug(),
                linkLifecycleEvent.originalUrl(),
                linkLifecycleEvent.title(),
                linkLifecycleEvent.tags(),
                linkLifecycleEvent.hostname(),
                linkLifecycleEvent.expiresAt(),
                linkLifecycleEvent.occurredAt());
    }

    private LinkLifecycleEvent deserialize(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, LinkLifecycleEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Lifecycle event payload could not be deserialized", exception);
        }
    }
}
