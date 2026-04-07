package com.linkplatform.api.link.api;

import java.util.List;

public record AbuseReviewQueueResponse(
        List<AbuseReviewCaseResponse> items,
        String nextCursor,
        boolean hasMore) {
}
