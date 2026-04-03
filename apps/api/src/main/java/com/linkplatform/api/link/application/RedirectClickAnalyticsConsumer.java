package com.linkplatform.api.link.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RedirectClickAnalyticsConsumer {

    private final ObjectMapper objectMapper;
    private final LinkStore linkStore;

    public RedirectClickAnalyticsConsumer(ObjectMapper objectMapper, LinkStore linkStore) {
        this.objectMapper = objectMapper;
        this.linkStore = linkStore;
    }

    @KafkaListener(topics = "${link-platform.analytics.click-topic}")
    public void consume(String payloadJson) {
        RedirectClickAnalyticsEvent redirectClickAnalyticsEvent = deserialize(payloadJson);
        linkStore.recordClickIfAbsent(new LinkClick(
                redirectClickAnalyticsEvent.eventId(),
                redirectClickAnalyticsEvent.slug(),
                redirectClickAnalyticsEvent.clickedAt(),
                redirectClickAnalyticsEvent.userAgent(),
                redirectClickAnalyticsEvent.referrer(),
                redirectClickAnalyticsEvent.remoteAddress()));
    }

    private RedirectClickAnalyticsEvent deserialize(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, RedirectClickAnalyticsEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Analytics event payload could not be deserialized", exception);
        }
    }
}
