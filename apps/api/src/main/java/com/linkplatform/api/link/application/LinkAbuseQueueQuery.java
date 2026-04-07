package com.linkplatform.api.link.application;

public record LinkAbuseQueueQuery(
        LinkAbuseCaseStatus status,
        LinkAbuseSource source,
        String slug,
        int limit,
        String cursor) {
}
