package com.linkplatform.api.link.application;

public record TrendingLink(
        String slug,
        String originalUrl,
        long clickGrowth,
        long currentWindowClicks,
        long previousWindowClicks) {
}
