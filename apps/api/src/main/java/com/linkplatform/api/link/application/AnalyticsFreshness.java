package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;

public record AnalyticsFreshness(
        OffsetDateTime asOf,
        OffsetDateTime latestMaterializedClickAt,
        OffsetDateTime latestMaterializedActivityAt,
        Long clickLagSeconds,
        Long activityLagSeconds) {
}
