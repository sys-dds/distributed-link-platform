package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface WorkspacePlanStore {

    Optional<WorkspacePlanRecord> findByWorkspaceId(long workspaceId);

    WorkspacePlanRecord upsertPlan(long workspaceId, WorkspacePlanCode planCode, OffsetDateTime updatedAt);
}
