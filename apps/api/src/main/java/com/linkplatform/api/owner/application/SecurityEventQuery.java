package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;
import java.util.List;

public record SecurityEventQuery(
        List<SecurityEventType> types,
        OffsetDateTime since,
        int limit,
        String cursor) {
}
