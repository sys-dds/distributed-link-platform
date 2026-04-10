package com.linkplatform.api.link.api;

import java.time.OffsetDateTime;
import java.util.Map;

public record OpsStatusResponse(
        String workspaceSlug,
        String runtimeMode,
        OffsetDateTime generatedAt,
        Map<String, Object> redirectRuntime,
        Map<String, Object> queryRuntime,
        Map<String, OpsPipelineSummaryResponse> pipelines,
        OpsProjectionSummaryResponse projectionJobs,
        OpsAbuseSummaryResponse abuse,
        GlobalGovernanceSummaryResponse governance) {
}
