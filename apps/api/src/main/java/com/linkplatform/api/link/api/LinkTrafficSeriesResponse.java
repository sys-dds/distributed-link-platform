package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.LinkApplicationService;
import java.time.OffsetDateTime;
import java.util.List;

public record LinkTrafficSeriesResponse(
        String slug,
        OffsetDateTime from,
        OffsetDateTime to,
        String granularity,
        List<LinkTrafficSeriesBucketResponse> buckets,
        AnalyticsFreshnessResponse freshness,
        AnalyticsComparisonResponse comparison) {

    public static LinkTrafficSeriesResponse from(LinkApplicationService.LinkTrafficSeriesView seriesView) {
        return new LinkTrafficSeriesResponse(
                seriesView.slug(),
                seriesView.from(),
                seriesView.to(),
                seriesView.granularity(),
                seriesView.buckets().stream().map(LinkTrafficSeriesBucketResponse::from).toList(),
                AnalyticsFreshnessResponse.from(seriesView.freshness()),
                AnalyticsComparisonResponse.from(seriesView.comparison()));
    }
}
