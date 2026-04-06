package com.linkplatform.api.link.application;

import java.time.Duration;
import java.time.OffsetDateTime;

public interface RedirectRateLimitStore {

    int increment(String subjectHash, String slug, OffsetDateTime bucketStartedAt, Duration window, OffsetDateTime expiresAt);
}
