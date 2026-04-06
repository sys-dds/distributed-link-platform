package com.linkplatform.api.owner.application;

public enum SecurityEventType {
    INVALID_API_KEY,
    RATE_LIMIT_REJECTED,
    QUOTA_REJECTED,
    REDIRECT_LOOKUP_FAILED,
    REDIRECT_FAILOVER_ACTIVATED,
    REDIRECT_UNAVAILABLE
}
