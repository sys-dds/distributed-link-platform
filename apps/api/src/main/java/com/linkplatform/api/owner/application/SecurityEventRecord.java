package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;
import java.util.Map;

public record SecurityEventRecord(
        long id,
        SecurityEventType type,
        OffsetDateTime occurredAt,
        String summary,
        Map<String, Object> metadata) {
}
