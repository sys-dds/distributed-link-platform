package com.linkplatform.api.link.api;

import java.util.List;

public record LinkTrafficBreakdownResponse(
        List<DailyClickBucketResponse> recentHourlyClicks,
        long ownerClicksLast1Hour,
        long ownerClicksLast24Hours,
        long ownerClicksLast7Days) {
}
