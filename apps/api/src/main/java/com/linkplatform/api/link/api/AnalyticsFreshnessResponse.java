package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.AnalyticsFreshness;
import java.time.OffsetDateTime;

public record AnalyticsFreshnessResponse(
        OffsetDateTime asOf,
        OffsetDateTime latestMaterializedClickAt,
        OffsetDateTime latestMaterializedActivityAt,
        Long clickLagSeconds,
        Long activityLagSeconds) {

    public static AnalyticsFreshnessResponse from(AnalyticsFreshness freshness) {
        if (freshness == null) {
            return null;
        }
        return new AnalyticsFreshnessResponse(
                freshness.asOf(),
                freshness.latestMaterializedClickAt(),
                freshness.latestMaterializedActivityAt(),
                freshness.clickLagSeconds(),
                freshness.activityLagSeconds());
    }
}
