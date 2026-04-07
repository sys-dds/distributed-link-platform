package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.LinkAbuseCaseRecord;
import java.time.OffsetDateTime;

public record AbuseReviewCaseResponse(
        long id,
        String slug,
        String status,
        String source,
        long signalCount,
        int riskScore,
        String summary,
        String detailSummary,
        String targetHost,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime reviewedAt,
        String resolution,
        String resolutionNote) {

    public static AbuseReviewCaseResponse from(LinkAbuseCaseRecord record) {
        return new AbuseReviewCaseResponse(
                record.id(),
                record.slug(),
                record.status().name().toLowerCase(java.util.Locale.ROOT),
                record.source().name().toLowerCase(java.util.Locale.ROOT),
                record.signalCount(),
                record.riskScore(),
                record.summary(),
                record.detailSummary(),
                record.targetHost(),
                record.createdAt(),
                record.updatedAt(),
                record.reviewedAt(),
                record.resolution() == null ? null : record.resolution().toLowerCase(java.util.Locale.ROOT),
                record.resolutionNote());
    }
}
