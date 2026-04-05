package com.linkplatform.api.link.api;

import java.util.List;

public record LinkDiscoveryPageResponse(
        List<LinkDiscoveryItemResponse> items,
        String nextCursor,
        boolean hasMore) {
}
