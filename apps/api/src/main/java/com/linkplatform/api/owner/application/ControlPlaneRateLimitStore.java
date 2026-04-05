package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;

public interface ControlPlaneRateLimitStore {

    boolean tryConsume(long ownerId, ControlPlaneRateLimitBucket bucket, OffsetDateTime windowStartedAt, int limit);
}
