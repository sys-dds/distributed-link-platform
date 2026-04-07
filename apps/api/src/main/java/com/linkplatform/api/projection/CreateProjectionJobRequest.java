package com.linkplatform.api.projection;

import java.time.OffsetDateTime;

public record CreateProjectionJobRequest(
        ProjectionJobType jobType,
        Long ownerId,
        String workspaceSlug,
        String slug,
        OffsetDateTime from,
        OffsetDateTime to,
        String operatorNote) {
}
