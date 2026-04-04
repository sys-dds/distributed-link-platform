package com.linkplatform.api.link.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class LinkLifecycleOutboxRelayTest {

    @Mock
    private LinkLifecycleOutboxStore linkLifecycleOutboxStore;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private SimpleMeterRegistry meterRegistry;
    private LinkLifecycleOutboxRelay relay;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        relay = new LinkLifecycleOutboxRelay(
                linkLifecycleOutboxStore,
                kafkaTemplate,
                meterRegistry,
                "link-platform.lifecycle.link-events",
                50,
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                Duration.ofMinutes(5),
                3,
                Clock.fixed(Instant.parse("2026-04-04T09:00:00Z"), ZoneOffset.UTC),
                "worker-a");
    }

    @Test
    void relayPublishesLifecycleOutboxRecordsUsingStablePerLinkKey() {
        LinkLifecycleOutboxRecord outboxRecord = new LinkLifecycleOutboxRecord(
                1L,
                "event-1",
                "CREATED",
                "launch-page",
                "{\"eventId\":\"event-1\"}",
                OffsetDateTime.parse("2026-04-04T08:59:00Z"),
                null,
                "worker-a",
                OffsetDateTime.parse("2026-04-04T09:00:30Z"),
                0,
                null,
                null,
                null);
        when(linkLifecycleOutboxStore.claimBatch(
                "worker-a",
                OffsetDateTime.parse("2026-04-04T09:00:00Z"),
                OffsetDateTime.parse("2026-04-04T09:00:30Z"),
                50))
                .thenReturn(List.of(outboxRecord));
        when(kafkaTemplate.send("link-platform.lifecycle.link-events", "launch-page", "{\"eventId\":\"event-1\"}"))
                .thenReturn(CompletableFuture.completedFuture(null));

        relay.relayPendingEvents();

        verify(kafkaTemplate).send("link-platform.lifecycle.link-events", "launch-page", "{\"eventId\":\"event-1\"}");
        verify(linkLifecycleOutboxStore).markPublished(eq(1L), any(OffsetDateTime.class));
        assertEquals(1.0, meterRegistry.get("link.lifecycle.outbox.publish.success").counter().count());
    }

    @Test
    void relaySchedulesRetryOnPublishFailure() {
        LinkLifecycleOutboxRecord outboxRecord = new LinkLifecycleOutboxRecord(
                2L,
                "event-2",
                "UPDATED",
                "launch-page",
                "{\"eventId\":\"event-2\"}",
                OffsetDateTime.parse("2026-04-04T08:59:00Z"),
                null,
                "worker-a",
                OffsetDateTime.parse("2026-04-04T09:00:30Z"),
                0,
                null,
                null,
                null);
        when(linkLifecycleOutboxStore.claimBatch(
                "worker-a",
                OffsetDateTime.parse("2026-04-04T09:00:00Z"),
                OffsetDateTime.parse("2026-04-04T09:00:30Z"),
                50))
                .thenReturn(List.of(outboxRecord));
        when(kafkaTemplate.send("link-platform.lifecycle.link-events", "launch-page", "{\"eventId\":\"event-2\"}"))
                .thenThrow(new RuntimeException("Kafka unavailable"));

        relay.relayPendingEvents();

        verify(linkLifecycleOutboxStore).recordPublishFailure(
                2L,
                1,
                OffsetDateTime.parse("2026-04-04T09:00:05Z"),
                "RuntimeException: Kafka unavailable",
                null);
        verify(linkLifecycleOutboxStore, never()).markPublished(any(Long.class), any(OffsetDateTime.class));
        assertEquals(1.0, meterRegistry.get("link.lifecycle.outbox.retry").counter().count());
    }

    @Test
    void relayParksLifecycleRowAfterMaxAttempts() {
        LinkLifecycleOutboxRecord outboxRecord = new LinkLifecycleOutboxRecord(
                3L,
                "event-3",
                "DELETED",
                "launch-page",
                "{\"eventId\":\"event-3\"}",
                OffsetDateTime.parse("2026-04-04T08:59:00Z"),
                null,
                "worker-a",
                OffsetDateTime.parse("2026-04-04T09:00:30Z"),
                2,
                OffsetDateTime.parse("2026-04-04T09:00:00Z"),
                "previous failure",
                null);
        when(linkLifecycleOutboxStore.claimBatch(
                "worker-a",
                OffsetDateTime.parse("2026-04-04T09:00:00Z"),
                OffsetDateTime.parse("2026-04-04T09:00:30Z"),
                50))
                .thenReturn(List.of(outboxRecord));
        when(kafkaTemplate.send("link-platform.lifecycle.link-events", "launch-page", "{\"eventId\":\"event-3\"}"))
                .thenThrow(new RuntimeException("Still failing"));

        relay.relayPendingEvents();

        verify(linkLifecycleOutboxStore).recordPublishFailure(
                3L,
                3,
                null,
                "RuntimeException: Still failing",
                OffsetDateTime.parse("2026-04-04T09:00:00Z"));
        assertEquals(1.0, meterRegistry.get("link.lifecycle.outbox.parked.transition").counter().count());
    }
}
