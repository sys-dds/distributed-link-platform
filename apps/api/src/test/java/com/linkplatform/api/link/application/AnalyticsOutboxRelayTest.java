package com.linkplatform.api.link.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class AnalyticsOutboxRelayTest {

    @Mock
    private AnalyticsOutboxStore analyticsOutboxStore;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private AnalyticsOutboxRelay analyticsOutboxRelay;

    @BeforeEach
    void setUp() {
        analyticsOutboxRelay = new AnalyticsOutboxRelay(
                analyticsOutboxStore,
                kafkaTemplate,
                "link-platform.analytics.redirect-clicks",
                50);
    }

    @Test
    void relayPublishesEligibleOutboxRecords() {
        AnalyticsOutboxRecord outboxRecord = new AnalyticsOutboxRecord(
                1L,
                "event-1",
                "redirect-click",
                "launch-page",
                "{\"eventId\":\"event-1\"}",
                OffsetDateTime.parse("2026-04-03T09:00:00Z"),
                null);
        when(analyticsOutboxStore.findUnpublished(50)).thenReturn(List.of(outboxRecord));
        when(kafkaTemplate.send(eq("link-platform.analytics.redirect-clicks"), eq("launch-page"), eq("{\"eventId\":\"event-1\"}")))
                .thenReturn(CompletableFuture.completedFuture(null));

        analyticsOutboxRelay.relayPendingEvents();

        verify(kafkaTemplate).send("link-platform.analytics.redirect-clicks", "launch-page", "{\"eventId\":\"event-1\"}");
        verify(analyticsOutboxStore).markPublished(eq(1L), any(OffsetDateTime.class));
    }
}
