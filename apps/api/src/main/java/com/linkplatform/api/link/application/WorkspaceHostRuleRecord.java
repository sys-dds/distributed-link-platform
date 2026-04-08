package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;

public record WorkspaceHostRuleRecord(
        long id,
        long workspaceId,
        String host,
        String ruleType,
        String note,
        OffsetDateTime createdAt,
        long createdByOwnerId) {
}
