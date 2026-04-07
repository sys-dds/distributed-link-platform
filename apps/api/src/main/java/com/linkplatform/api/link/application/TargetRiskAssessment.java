package com.linkplatform.api.link.application;

public record TargetRiskAssessment(
        Decision decision,
        String summary,
        int riskScore,
        String normalizedTargetHost) {

    public enum Decision {
        SAFE,
        REJECT,
        REVIEW
    }

    public boolean safe() {
        return decision == Decision.SAFE;
    }

    public boolean reject() {
        return decision == Decision.REJECT;
    }

    public boolean review() {
        return decision == Decision.REVIEW;
    }
}
