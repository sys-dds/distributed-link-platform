package com.linkplatform.api.link.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.mockito.InOrder;
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

    private SimpleMeterRegistry meterRegistry;
    private AnalyticsOutboxRelay analyticsOutboxRelay;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        analyticsOutboxRelay = new AnalyticsOutboxRelay(
                analyticsOutboxStore,
                kafkaTemplate,
                meterRegistry,
                "link-platform.analytics.redirect-clicks",
                50,
                Duration.ofSeconds(30),
                Clock.fixed(Instant.parse("2026-04-03T09:00:00Z"), ZoneOffset.UTC),
                "worker-a");
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
                null,
                "worker-a",
                OffsetDateTime.parse("2026-04-03T09:00:30Z"));
        when(analyticsOutboxStore.claimBatch(
                "worker-a",
                OffsetDateTime.parse("2026-04-03T09:00:00Z"),
                OffsetDateTime.parse("2026-04-03T09:00:30Z"),
                50))
                .thenReturn(List.of(outboxRecord));
        when(kafkaTemplate.send(eq("link-platform.analytics.redirect-clicks"), eq("launch-page"), eq("{\"eventId\":\"event-1\"}")))
                .thenReturn(CompletableFuture.completedFuture(null));

        analyticsOutboxRelay.relayPendingEvents();

        verify(kafkaTemplate).send("link-platform.analytics.redirect-clicks", "launch-page", "{\"eventId\":\"event-1\"}");
        verify(analyticsOutboxStore).markPublished(eq(1L), any(OffsetDateTime.class));
        assertEquals(1.0, meterRegistry.get("link.analytics.outbox.publish.success").counter().count());
        assertEquals(0.0, meterRegistry.get("link.analytics.outbox.publish.failure").counter().count());
    }

    @Test
    void relayUsesStablePerLinkKeyingAndPreservesOutboxOrder() {
        AnalyticsOutboxRecord firstRecord = new AnalyticsOutboxRecord(
                1L,
                "event-1",
                "redirect-click",
                "launch-page",
                "{\"eventId\":\"event-1\"}",
                OffsetDateTime.parse("2026-04-03T09:00:00Z"),
                null,
                "worker-a",
                OffsetDateTime.parse("2026-04-03T09:00:30Z"));
        AnalyticsOutboxRecord secondRecord = new AnalyticsOutboxRecord(
                2L,
                "event-2",
                "redirect-click",
                "launch-page",
                "{\"eventId\":\"event-2\"}",
                OffsetDateTime.parse("2026-04-03T09:00:01Z"),
                null,
                "worker-a",
                OffsetDateTime.parse("2026-04-03T09:00:30Z"));
        when(analyticsOutboxStore.claimBatch(
                "worker-a",
                OffsetDateTime.parse("2026-04-03T09:00:00Z"),
                OffsetDateTime.parse("2026-04-03T09:00:30Z"),
                50))
                .thenReturn(List.of(firstRecord, secondRecord));
        when(kafkaTemplate.send(eq("link-platform.analytics.redirect-clicks"), eq("launch-page"), eq("{\"eventId\":\"event-1\"}")))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(kafkaTemplate.send(eq("link-platform.analytics.redirect-clicks"), eq("launch-page"), eq("{\"eventId\":\"event-2\"}")))
                .thenReturn(CompletableFuture.completedFuture(null));

        analyticsOutboxRelay.relayPendingEvents();

        InOrder inOrder = inOrder(kafkaTemplate, analyticsOutboxStore);
        inOrder.verify(kafkaTemplate).send("link-platform.analytics.redirect-clicks", "launch-page", "{\"eventId\":\"event-1\"}");
        inOrder.verify(analyticsOutboxStore).markPublished(eq(1L), any(OffsetDateTime.class));
        inOrder.verify(kafkaTemplate).send("link-platform.analytics.redirect-clicks", "launch-page", "{\"eventId\":\"event-2\"}");
        inOrder.verify(analyticsOutboxStore).markPublished(eq(2L), any(OffsetDateTime.class));
        assertEquals(2.0, meterRegistry.get("link.analytics.outbox.publish.success").counter().count());
    }

    @Test
    void relayCountsPublishFailuresAndLeavesRecordUnpublished() {
        AnalyticsOutboxRecord outboxRecord = new AnalyticsOutboxRecord(
                1L,
                "event-1",
                "redirect-click",
                "launch-page",
                "{\"eventId\":\"event-1\"}",
                OffsetDateTime.parse("2026-04-03T09:00:00Z"),
                null,
                "worker-a",
                OffsetDateTime.parse("2026-04-03T09:00:30Z"));
        when(analyticsOutboxStore.claimBatch(
                "worker-a",
                OffsetDateTime.parse("2026-04-03T09:00:00Z"),
                OffsetDateTime.parse("2026-04-03T09:00:30Z"),
                50))
                .thenReturn(List.of(outboxRecord));
        when(kafkaTemplate.send("link-platform.analytics.redirect-clicks", "launch-page", "{\"eventId\":\"event-1\"}"))
                .thenThrow(new RuntimeException("Kafka unavailable"));

        assertThrows(RuntimeException.class, () -> analyticsOutboxRelay.relayPendingEvents());

        verify(kafkaTemplate).send("link-platform.analytics.redirect-clicks", "launch-page", "{\"eventId\":\"event-1\"}");
        assertEquals(0.0, meterRegistry.get("link.analytics.outbox.publish.success").counter().count());
        assertEquals(1.0, meterRegistry.get("link.analytics.outbox.publish.failure").counter().count());
    }
}
