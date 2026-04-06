package com.linkplatform.api.link.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkplatform.api.runtime.ConditionalOnRuntimeModes;
import com.linkplatform.api.runtime.RuntimeMode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnRuntimeModes({RuntimeMode.ALL, RuntimeMode.WORKER})
public class RedirectClickAnalyticsConsumer {

    private final ObjectMapper objectMapper;
    private final LinkStore linkStore;
    private final LinkReadCache linkReadCache;
    private final Counter processedCounter;
    private final Counter duplicateCounter;

    public RedirectClickAnalyticsConsumer(
            ObjectMapper objectMapper,
            LinkStore linkStore,
            LinkReadCache linkReadCache,
            MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.linkStore = linkStore;
        this.linkReadCache = linkReadCache;
        this.processedCounter = Counter.builder("link.analytics.consumer.processed")
                .description("Number of redirect analytics events processed")
                .register(meterRegistry);
        this.duplicateCounter = Counter.builder("link.analytics.consumer.duplicate")
                .description("Number of duplicate redirect analytics events ignored")
                .register(meterRegistry);
    }

    @KafkaListener(topics = "${link-platform.analytics.click-topic}")
    public void consume(String payloadJson) {
        RedirectClickAnalyticsEvent redirectClickAnalyticsEvent = deserialize(payloadJson);
        boolean persisted = linkStore.recordClickIfAbsent(new LinkClick(
                redirectClickAnalyticsEvent.eventId(),
                redirectClickAnalyticsEvent.slug(),
                redirectClickAnalyticsEvent.clickedAt(),
                redirectClickAnalyticsEvent.userAgent(),
                redirectClickAnalyticsEvent.referrer(),
                redirectClickAnalyticsEvent.remoteAddress()));
        if (persisted) {
            linkStore.findOwnerIdBySlug(redirectClickAnalyticsEvent.slug())
                    .ifPresent(ownerId -> {
                        linkStore.findStoredDetailsBySlug(redirectClickAnalyticsEvent.slug(), ownerId)
                                .ifPresent(linkDetails -> linkStore.recordActivityIfAbsent(
                                        redirectClickAnalyticsEvent.eventId(),
                                        new LinkActivityEvent(
                                                ownerId,
                                                LinkActivityType.CLICKED,
                                                linkDetails.slug(),
                                                linkDetails.originalUrl(),
                                                linkDetails.title(),
                                                linkDetails.tags(),
                                                linkDetails.hostname(),
                                                linkDetails.expiresAt(),
                                                redirectClickAnalyticsEvent.clickedAt())));
                        linkReadCache.invalidateOwnerAnalytics(ownerId);
                    });
            processedCounter.increment();
            return;
        }
        duplicateCounter.increment();
    }

    private RedirectClickAnalyticsEvent deserialize(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, RedirectClickAnalyticsEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Analytics event payload could not be deserialized", exception);
        }
    }
}
