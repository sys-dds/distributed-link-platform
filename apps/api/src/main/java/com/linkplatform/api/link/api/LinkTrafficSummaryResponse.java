package com.linkplatform.api.link.api;

import java.time.OffsetDateTime;
import java.util.List;

public record LinkTrafficSummaryResponse(
        String slug,
        String originalUrl,
        long totalClicks,
        long clicksLast24Hours,
        long clicksLast7Days,
        List<DailyClickBucketResponse> recentDailyClicks,
        List<TopReferrerResponse> topReferrers,
        LinkTrafficBreakdownResponse trafficBreakdown,
        OffsetDateTime windowStart,
        OffsetDateTime windowEnd,
        Long windowClicks,
        AnalyticsFreshnessResponse freshness,
        AnalyticsComparisonResponse comparison) {

    public LinkTrafficSummaryResponse(
            String slug,
            String originalUrl,
            long totalClicks,
            long clicksLast24Hours,
            long clicksLast7Days,
            List<DailyClickBucketResponse> recentDailyClicks) {
        this(
                slug,
                originalUrl,
                totalClicks,
                clicksLast24Hours,
                clicksLast7Days,
                recentDailyClicks,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
