package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.TrendingLink;

public record TrendingLinkResponse(
        String slug,
        String originalUrl,
        long clickGrowth,
        long currentWindowClicks,
        long previousWindowClicks) {

    public static TrendingLinkResponse from(TrendingLink trendingLink) {
        return new TrendingLinkResponse(
                trendingLink.slug(),
                trendingLink.originalUrl(),
                trendingLink.clickGrowth(),
                trendingLink.currentWindowClicks(),
                trendingLink.previousWindowClicks());
    }
}
