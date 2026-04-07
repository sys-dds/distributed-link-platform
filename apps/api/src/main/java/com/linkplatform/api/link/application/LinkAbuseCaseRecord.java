package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;

public record LinkAbuseCaseRecord(
        long id,
        long workspaceId,
        String slug,
        LinkAbuseCaseStatus status,
        LinkAbuseSource source,
        long signalCount,
        int riskScore,
        String summary,
        String detailSummary,
        String targetHost,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Long createdByOwnerId,
        OffsetDateTime reviewedAt,
        Long reviewedByOwnerId,
        String resolution,
        String resolutionNote) {
}
