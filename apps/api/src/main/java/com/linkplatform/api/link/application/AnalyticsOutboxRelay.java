package com.linkplatform.api.link.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsOutboxRelay {

    private final AnalyticsOutboxStore analyticsOutboxStore;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;
    private final String clickTopic;
    private final int batchSize;

    public AnalyticsOutboxRelay(
            AnalyticsOutboxStore analyticsOutboxStore,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${link-platform.analytics.click-topic}") String clickTopic,
            @Value("${link-platform.analytics.outbox-relay-batch-size}") int batchSize) {
        this.analyticsOutboxStore = analyticsOutboxStore;
        this.kafkaTemplate = kafkaTemplate;
        this.clickTopic = clickTopic;
        this.batchSize = batchSize;
        this.clock = Clock.systemUTC();
    }

    @Scheduled(fixedDelayString = "${link-platform.analytics.outbox-relay-delay}")
    public void relayPendingEvents() {
        List<AnalyticsOutboxRecord> unpublishedRecords = analyticsOutboxStore.findUnpublished(batchSize);

        for (AnalyticsOutboxRecord unpublishedRecord : unpublishedRecords) {
            kafkaTemplate.send(clickTopic, unpublishedRecord.eventKey(), unpublishedRecord.payloadJson()).join();
            analyticsOutboxStore.markPublished(unpublishedRecord.id(), OffsetDateTime.now(clock));
        }
    }
}
