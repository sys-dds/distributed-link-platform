package com.linkplatform.api.link.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.linkplatform.api.link.application.LinkLifecycleEvent;
import com.linkplatform.api.link.application.LinkLifecycleEventType;
import com.linkplatform.api.link.application.RedirectClickAnalyticsEvent;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContractCompatibilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void redirectClickAnalyticsEventJsonShapeRemainsCompatible() throws Exception {
        String json = objectMapper.writeValueAsString(new RedirectClickAnalyticsEvent(
                "event-1",
                "launch-page",
                OffsetDateTime.parse("2026-04-06T08:00:00Z"),
                "test-agent",
                "https://referrer.example",
                "127.0.0.1"));

        assertThat(json).isEqualTo(
                "{\"eventId\":\"event-1\",\"slug\":\"launch-page\",\"clickedAt\":\"2026-04-06T08:00:00Z\",\"userAgent\":\"test-agent\",\"referrer\":\"https://referrer.example\",\"remoteAddress\":\"127.0.0.1\"}");
    }

    @Test
    void linkLifecycleEventJsonShapeRemainsCompatible() throws Exception {
        String json = objectMapper.writeValueAsString(new LinkLifecycleEvent(
                "event-2",
                LinkLifecycleEventType.UPDATED,
                1L,
                "launch-page",
                "https://example.com/launch",
                "Launch",
                List.of("docs", "product"),
                "example.com",
                OffsetDateTime.parse("2030-04-01T08:00:00Z"),
                4L,
                OffsetDateTime.parse("2026-04-06T08:01:00Z")));

        assertThat(json).isEqualTo(
                "{\"eventId\":\"event-2\",\"eventType\":\"UPDATED\",\"ownerId\":1,\"slug\":\"launch-page\",\"originalUrl\":\"https://example.com/launch\",\"title\":\"Launch\",\"tags\":[\"docs\",\"product\"],\"hostname\":\"example.com\",\"expiresAt\":\"2030-04-01T08:00:00Z\",\"version\":4,\"occurredAt\":\"2026-04-06T08:01:00Z\"}");
    }

    @Test
    void linkReadResponseJsonShapeRemainsCompatible() throws Exception {
        String json = objectMapper.writeValueAsString(new LinkReadResponse(
                "launch-page",
                "https://example.com/launch",
                OffsetDateTime.parse("2026-04-01T08:00:00Z"),
                OffsetDateTime.parse("2030-04-01T08:00:00Z"),
                "Launch",
                List.of("docs"),
                "example.com",
                4L,
                12L));

        assertThat(json).isEqualTo(
                "{\"slug\":\"launch-page\",\"originalUrl\":\"https://example.com/launch\",\"createdAt\":\"2026-04-01T08:00:00Z\",\"expiresAt\":\"2030-04-01T08:00:00Z\",\"title\":\"Launch\",\"tags\":[\"docs\"],\"hostname\":\"example.com\",\"version\":4,\"clickTotal\":12}");
    }

    @Test
    void trafficSummaryResponseJsonShapeRemainsCompatible() throws Exception {
        String json = objectMapper.writeValueAsString(new LinkTrafficSummaryResponse(
                "launch-page",
                "https://example.com/launch",
                12L,
                3L,
                9L,
                List.of(new DailyClickBucketResponse(LocalDate.parse("2026-04-06"), 3L))));

        assertThat(json).isEqualTo(
                "{\"slug\":\"launch-page\",\"originalUrl\":\"https://example.com/launch\",\"totalClicks\":12,\"clicksLast24Hours\":3,\"clicksLast7Days\":9,\"recentDailyClicks\":[{\"day\":\"2026-04-06\",\"clickTotal\":3}]}");
    }
}
