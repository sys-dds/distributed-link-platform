package com.linkplatform.api.link.application;

import java.util.List;

public record LinkDiscoveryPage(
        List<LinkDiscoveryItem> items,
        String nextCursor,
        boolean hasMore) {
}
