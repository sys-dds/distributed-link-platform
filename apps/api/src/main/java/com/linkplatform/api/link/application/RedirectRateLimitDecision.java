package com.linkplatform.api.link.application;

public record RedirectRateLimitDecision(
        boolean allowed,
        boolean fallbackStoreUsed,
        boolean degradedStore,
        int requestCount,
        int limit) {

    public static RedirectRateLimitDecision allowed(
            boolean fallbackStoreUsed,
            boolean degradedStore,
            int requestCount,
            int limit) {
        return new RedirectRateLimitDecision(true, fallbackStoreUsed, degradedStore, requestCount, limit);
    }

    public static RedirectRateLimitDecision rejected(
            boolean fallbackStoreUsed,
            boolean degradedStore,
            int requestCount,
            int limit) {
        return new RedirectRateLimitDecision(false, fallbackStoreUsed, degradedStore, requestCount, limit);
    }
}
