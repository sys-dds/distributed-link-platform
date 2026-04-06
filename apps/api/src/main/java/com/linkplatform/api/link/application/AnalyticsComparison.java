package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;

public record AnalyticsComparison(
        OffsetDateTime previousWindowStart,
        OffsetDateTime previousWindowEnd,
        long currentWindowClicks,
        long previousWindowClicks,
        long clickChangeAbsolute,
        double clickChangePercent) {

    public static AnalyticsComparison of(AnalyticsRange range, long currentWindowClicks, long previousWindowClicks) {
        long clickChangeAbsolute = currentWindowClicks - previousWindowClicks;
        double clickChangePercent;
        if (previousWindowClicks == 0L && currentWindowClicks == 0L) {
            clickChangePercent = 0D;
        } else if (previousWindowClicks == 0L) {
            clickChangePercent = 100D;
        } else {
            clickChangePercent = ((double) clickChangeAbsolute / (double) previousWindowClicks) * 100D;
        }
        return new AnalyticsComparison(
                range.previousStart(),
                range.previousEnd(),
                currentWindowClicks,
                previousWindowClicks,
                clickChangeAbsolute,
                clickChangePercent);
    }
}
