package com.linkplatform.api.link.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkplatform.api.runtime.ConditionalOnRuntimeModes;
import com.linkplatform.api.runtime.RuntimeMode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnRuntimeModes({RuntimeMode.ALL, RuntimeMode.WORKER})
public class LinkLifecycleConsumer {

    private final ObjectMapper objectMapper;
    private final LinkStore linkStore;
    private final LinkReadCache linkReadCache;
    private final Counter processedCounter;
    private final Counter duplicateCounter;

    public LinkLifecycleConsumer(
            ObjectMapper objectMapper,
            LinkStore linkStore,
            LinkReadCache linkReadCache,
            MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.linkStore = linkStore;
        this.linkReadCache = linkReadCache;
        this.processedCounter = Counter.builder("link.lifecycle.consumer.processed")
                .description("Number of lifecycle events processed")
                .register(meterRegistry);
        this.duplicateCounter = Counter.builder("link.lifecycle.consumer.duplicate")
                .description("Number of duplicate lifecycle events ignored")
                .register(meterRegistry);
    }

    @KafkaListener(topics = "${link-platform.lifecycle.topic}")
    @Transactional
    public void consume(String payloadJson) {
        LinkLifecycleEvent linkLifecycleEvent = deserialize(payloadJson);
        boolean persisted = linkStore.recordActivityIfAbsent(
                linkLifecycleEvent.eventId(),
                toActivityEvent(linkLifecycleEvent));
        if (persisted) {
            linkStore.projectCatalogEvent(linkLifecycleEvent);
            linkStore.projectDiscoveryEvent(linkLifecycleEvent);
            linkReadCache.invalidateOwnerControlPlane(linkLifecycleEvent.ownerId());
            linkReadCache.invalidateOwnerAnalytics(linkLifecycleEvent.ownerId());
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
                linkLifecycleEvent.ownerId(),
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
