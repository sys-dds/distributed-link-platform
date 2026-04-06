package com.linkplatform.api.link.api;

public record BulkLinkActionItemResponse(
        String slug,
        boolean success,
        Long newVersion,
        String errorCategory,
        String errorDetail) {
}
