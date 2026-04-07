package com.linkplatform.api.link.api;

public record ManualAbuseReviewRequest(
        String slug,
        String summary,
        String detailSummary,
        Integer riskScore,
        Boolean quarantineNow) {

    public boolean quarantineNowValue() {
        return Boolean.TRUE.equals(quarantineNow);
    }
}
