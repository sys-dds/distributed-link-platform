package com.linkplatform.api.link.application;

import java.util.List;

public record LinkTrafficSummary(
        String slug,
        String originalUrl,
        long totalClicks,
        long clicksLast24Hours,
        long clicksLast7Days,
        List<DailyClickBucket> recentDailyClicks) {
}
