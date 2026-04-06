package com.linkplatform.api.link.api;

import java.util.List;

public record BulkLinkActionResponse(
        String action,
        List<BulkLinkActionItemResponse> items) {
}
