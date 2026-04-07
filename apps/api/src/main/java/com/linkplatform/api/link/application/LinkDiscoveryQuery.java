package com.linkplatform.api.link.application;

public record LinkDiscoveryQuery(
        String searchText,
        String hostname,
        String tag,
        LinkAbuseStatus abuseStatus,
        LinkDiscoveryLifecycleFilter lifecycle,
        LinkDiscoveryExpirationFilter expiration,
        LinkDiscoverySort sort,
        int limit,
        String cursor) {
}
