package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.WorkspaceHostRuleRecord;
import java.time.OffsetDateTime;

public record WorkspaceHostRuleResponse(
        long id,
        String host,
        String ruleType,
        String note,
        OffsetDateTime createdAt,
        long createdByOwnerId) {

    static WorkspaceHostRuleResponse from(WorkspaceHostRuleRecord record) {
        return new WorkspaceHostRuleResponse(
                record.id(),
                record.host(),
                record.ruleType(),
                record.note(),
                record.createdAt(),
                record.createdByOwnerId());
    }
}
