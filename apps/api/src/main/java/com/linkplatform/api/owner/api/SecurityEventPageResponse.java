package com.linkplatform.api.owner.api;

import java.util.List;

public record SecurityEventPageResponse(
        List<SecurityEventResponse> items,
        String nextCursor,
        int limit) {
}
