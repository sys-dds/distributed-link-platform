package com.linkplatform.api.link.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.linkplatform.api.link.application.LinkLifecycleEvent;
import com.linkplatform.api.link.application.LinkLifecycleEventType;
import com.linkplatform.api.link.application.RedirectClickAnalyticsEvent;
import com.linkplatform.api.owner.api.MeResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

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
    void linkResponseJsonShapeRemainsCompatible() throws Exception {
        String json = objectMapper.writeValueAsString(new LinkResponse(
                "launch-page",
                "https://example.com/launch",
                OffsetDateTime.parse("2026-04-01T08:00:00Z"),
                OffsetDateTime.parse("2030-04-01T08:00:00Z"),
                "Launch",
                List.of("docs"),
                "example.com",
                4L));

        assertThat(json).isEqualTo(
                "{\"slug\":\"launch-page\",\"originalUrl\":\"https://example.com/launch\",\"createdAt\":\"2026-04-01T08:00:00Z\",\"expiresAt\":\"2030-04-01T08:00:00Z\",\"title\":\"Launch\",\"tags\":[\"docs\"],\"hostname\":\"example.com\",\"version\":4}");
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

    @Test
    void linkDiscoveryPageResponseJsonShapeRemainsCompatible() throws Exception {
        String json = objectMapper.writeValueAsString(new LinkDiscoveryPageResponse(
                List.of(new LinkDiscoveryItemResponse(
                        "launch-page",
                        "https://example.com/launch",
                        "Launch",
                        "example.com",
                        List.of("docs"),
                        "ACTIVE",
                        OffsetDateTime.parse("2026-04-01T08:00:00Z"),
                        OffsetDateTime.parse("2026-04-06T08:00:00Z"),
                        OffsetDateTime.parse("2030-04-01T08:00:00Z"),
                        null,
                        4L)),
                "next-cursor",
                true));

        assertThat(json).isEqualTo(
                "{\"items\":[{\"slug\":\"launch-page\",\"originalUrl\":\"https://example.com/launch\",\"title\":\"Launch\",\"hostname\":\"example.com\",\"tags\":[\"docs\"],\"lifecycleState\":\"ACTIVE\",\"createdAt\":\"2026-04-01T08:00:00Z\",\"updatedAt\":\"2026-04-06T08:00:00Z\",\"expiresAt\":\"2030-04-01T08:00:00Z\",\"deletedAt\":null,\"version\":4}],\"nextCursor\":\"next-cursor\",\"hasMore\":true}");
    }

    @Test
    void trendingLinkResponseJsonShapeRemainsCompatible() throws Exception {
        String json = objectMapper.writeValueAsString(new TrendingLinkResponse(
                "launch-page",
                "https://example.com/launch",
                7L,
                10L,
                3L));

        assertThat(json).isEqualTo(
                "{\"slug\":\"launch-page\",\"originalUrl\":\"https://example.com/launch\",\"clickGrowth\":7,\"currentWindowClicks\":10,\"previousWindowClicks\":3}");
    }

    @Test
    void meResponseJsonShapeRemainsCompatible() throws Exception {
        String json = objectMapper.writeValueAsString(new MeResponse(
                "free-owner",
                "Free Owner",
                "FREE",
                2L,
                2L));

        assertThat(json).isEqualTo(
                "{\"ownerKey\":\"free-owner\",\"displayName\":\"Free Owner\",\"plan\":\"FREE\",\"activeLinkCount\":2,\"activeLinkLimit\":2}");
    }

    @Test
    void linkActivityEventResponseJsonShapeRemainsCompatible() throws Exception {
        String json = objectMapper.writeValueAsString(new LinkActivityEventResponse(
                "created",
                "launch-page",
                "https://example.com/launch",
                "Launch",
                List.of("docs"),
                "example.com",
                OffsetDateTime.parse("2030-04-01T08:00:00Z"),
                OffsetDateTime.parse("2026-04-06T08:00:00Z")));

        assertThat(json).isEqualTo(
                "{\"type\":\"created\",\"slug\":\"launch-page\",\"originalUrl\":\"https://example.com/launch\",\"title\":\"Launch\",\"tags\":[\"docs\"],\"hostname\":\"example.com\",\"expiresAt\":\"2030-04-01T08:00:00Z\",\"occurredAt\":\"2026-04-06T08:00:00Z\"}");
    }

    @Test
    void unauthorizedProblemDetailJsonShapeRemainsCompatible() throws Exception {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "X-API-Key header is required");
        problemDetail.setTitle(HttpStatus.UNAUTHORIZED.getReasonPhrase());

        String json = objectMapper.writeValueAsString(problemDetail);

        assertThat(json).isEqualTo(
                "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"X-API-Key header is required\",\"instance\":null,\"properties\":null}");
    }

    @Test
    void serviceUnavailableProblemDetailJsonShapeRemainsCompatible() throws Exception {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Redirect lookup temporarily unavailable for slug: launch-page");
        problemDetail.setTitle(HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase());

        String json = objectMapper.writeValueAsString(problemDetail);

        assertThat(json).isEqualTo(
                "{\"type\":\"about:blank\",\"title\":\"Service Unavailable\",\"status\":503,\"detail\":\"Redirect lookup temporarily unavailable for slug: launch-page\",\"instance\":null,\"properties\":null}");
    }
}
