package com.linkplatform.api.link.api;

import java.util.List;

public record LinkTrafficSummaryResponse(
        String slug,
        String originalUrl,
        long totalClicks,
        long clicksLast24Hours,
        long clicksLast7Days,
        List<DailyClickBucketResponse> recentDailyClicks,
        List<TopReferrerResponse> topReferrers,
        LinkTrafficBreakdownResponse trafficBreakdown) {
}
