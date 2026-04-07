package com.linkplatform.api.link.api;

import java.util.List;

public record OperatorActionLogPageResponse(
        List<OperatorActionLogResponse> items,
        String nextCursor,
        boolean hasMore) {
}
