package com.linkplatform.api.owner.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SecurityEventResponse(
        long id,
        String type,
        OffsetDateTime occurredAt,
        String summary,
        Map<String, Object> metadata) {
}
