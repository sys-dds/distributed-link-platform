package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.AnalyticsComparison;
import java.time.OffsetDateTime;

public record AnalyticsComparisonResponse(
        OffsetDateTime previousWindowStart,
        OffsetDateTime previousWindowEnd,
        long currentWindowClicks,
        long previousWindowClicks,
        long clickChangeAbsolute,
        double clickChangePercent) {

    public static AnalyticsComparisonResponse from(AnalyticsComparison comparison) {
        if (comparison == null) {
            return null;
        }
        return new AnalyticsComparisonResponse(
                comparison.previousWindowStart(),
                comparison.previousWindowEnd(),
                comparison.currentWindowClicks(),
                comparison.previousWindowClicks(),
                comparison.clickChangeAbsolute(),
                comparison.clickChangePercent());
    }
}
