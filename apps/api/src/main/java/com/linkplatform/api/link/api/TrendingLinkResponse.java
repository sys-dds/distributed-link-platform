package com.linkplatform.api.link.api;

public record TrendingLinkResponse(
        String slug,
        String originalUrl,
        long clickGrowth,
        long currentWindowClicks,
        long previousWindowClicks) {
}
