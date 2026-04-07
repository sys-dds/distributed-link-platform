package com.linkplatform.api.link.api;

import com.linkplatform.api.owner.application.OperatorActionLogRecord;
import java.time.OffsetDateTime;

public record OperatorActionLogResponse(
        long id,
        String subsystem,
        String actionType,
        String targetSlug,
        Long targetCaseId,
        Long targetProjectionJobId,
        String note,
        OffsetDateTime createdAt,
        long actorOwnerId) {

    static OperatorActionLogResponse from(OperatorActionLogRecord record) {
        return new OperatorActionLogResponse(
                record.id(),
                record.subsystem(),
                record.actionType(),
                record.targetSlug(),
                record.targetCaseId(),
                record.targetProjectionJobId(),
                record.note(),
                record.createdAt(),
                record.ownerId());
    }
}
