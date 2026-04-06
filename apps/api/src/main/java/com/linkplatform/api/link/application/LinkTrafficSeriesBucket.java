package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;

public record LinkTrafficSeriesBucket(
        OffsetDateTime bucketStart,
        OffsetDateTime bucketEnd,
        long clickTotal) {
}
