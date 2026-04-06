package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.LinkTrafficSeriesBucket;
import java.time.OffsetDateTime;

public record LinkTrafficSeriesBucketResponse(
        OffsetDateTime bucketStart,
        OffsetDateTime bucketEnd,
        long clickTotal) {

    public static LinkTrafficSeriesBucketResponse from(LinkTrafficSeriesBucket bucket) {
        return new LinkTrafficSeriesBucketResponse(
                bucket.bucketStart(),
                bucket.bucketEnd(),
                bucket.clickTotal());
    }
}
