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
                    .ifPresent(linkReadCache::invalidateOwnerAnalytics);
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
