package com.linkplatform.api.link.api;

import java.time.OffsetDateTime;

public record OpsAbuseSummaryResponse(
        long openCount,
        long quarantinedCount,
        long releasedTodayCount,
        long dismissedTodayCount,
        OffsetDateTime latestUpdatedAt) {
}
