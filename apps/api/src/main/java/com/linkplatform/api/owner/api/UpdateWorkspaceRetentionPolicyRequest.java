package com.linkplatform.api.owner.api;

public record UpdateWorkspaceRetentionPolicyRequest(
        int clickHistoryDays,
        int securityEventsDays,
        int webhookDeliveriesDays,
        int abuseCasesDays,
        int operatorActionLogDays) {
}
