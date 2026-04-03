package com.linkplatform.api.link.application;

public record LinkTrafficSummaryTotals(
        String slug,
        String originalUrl,
        long totalClicks,
        long clicksLast24Hours,
        long clicksLast7Days) {
}
