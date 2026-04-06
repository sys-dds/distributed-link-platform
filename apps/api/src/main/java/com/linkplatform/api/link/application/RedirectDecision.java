package com.linkplatform.api.link.application;

public record RedirectDecision(
        String location,
        boolean recordAnalytics,
        boolean failoverActivated) {
}
