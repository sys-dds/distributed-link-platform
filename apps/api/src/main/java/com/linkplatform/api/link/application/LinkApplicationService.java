package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import com.linkplatform.api.owner.application.AuthenticatedOwner;
import java.time.OffsetDateTime;
import java.util.List;

public interface LinkApplicationService {

    record AnalyticsSummaryView(
            LinkTrafficSummary summary,
            OffsetDateTime windowStart,
            OffsetDateTime windowEnd,
            Long windowClicks,
            AnalyticsFreshness freshness,
            AnalyticsComparison comparison) {
    }

    record LinkTrafficSeriesView(
            String slug,
            OffsetDateTime from,
            OffsetDateTime to,
            String granularity,
            List<LinkTrafficSeriesBucket> buckets,
            AnalyticsFreshness freshness,
            AnalyticsComparison comparison) {
    }

    LinkMutationResult createLink(AuthenticatedOwner owner, CreateLinkCommand command, String idempotencyKey);

    LinkMutationResult updateLink(
            AuthenticatedOwner owner,
            String slug,
            String originalUrl,
            java.time.OffsetDateTime expiresAt,
            String title,
            List<String> tags,
            long expectedVersion,
            String idempotencyKey);

    LinkMutationResult deleteLink(AuthenticatedOwner owner, String slug, long expectedVersion, String idempotencyKey);

    LinkMutationResult changeLifecycle(
            AuthenticatedOwner owner,
            String slug,
            String action,
            OffsetDateTime expiresAt,
            long expectedVersion,
            String idempotencyKey);

    List<BulkLinkActionResult> bulkAction(
            AuthenticatedOwner owner,
            String action,
            List<String> slugs,
            List<String> tags,
            OffsetDateTime expiresAt,
            String idempotencyKey);

    Link resolveLink(String slug);

    void recordRedirectClick(String slug, String userAgent, String referrer, String remoteAddress);

    LinkDetails getLink(AuthenticatedOwner owner, String slug);

    List<LinkDetails> listRecentLinks(AuthenticatedOwner owner, int limit, String query, LinkLifecycleState state);

    List<LinkSuggestion> suggestLinks(AuthenticatedOwner owner, String query, int limit);

    LinkDiscoveryPage searchLinks(AuthenticatedOwner owner, LinkDiscoveryQuery query);

    long countActiveLinks(AuthenticatedOwner owner);

    List<LinkActivityEvent> getRecentActivity(AuthenticatedOwner owner, int limit);

    List<LinkActivityEvent> getRecentActivity(AuthenticatedOwner owner, int limit, String tag, String lifecycle);

    LinkTrafficSummary getTrafficSummary(AuthenticatedOwner owner, String slug);

    AnalyticsSummaryView getTrafficSummary(AuthenticatedOwner owner, String slug, AnalyticsRange range);

    LinkTrafficSeriesView getTrafficSeries(
            AuthenticatedOwner owner,
            String slug,
            AnalyticsRange range,
            String granularity);

    List<TopLinkTraffic> getTopLinks(AuthenticatedOwner owner, LinkTrafficWindow window);

    List<TopLinkTraffic> getTopLinks(
            AuthenticatedOwner owner,
            LinkTrafficWindow window,
            AnalyticsRange range,
            String tag,
            String lifecycle,
            int limit);

    List<TrendingLink> getTrendingLinks(AuthenticatedOwner owner, LinkTrafficWindow window, int limit);

    List<TrendingLink> getTrendingLinks(
            AuthenticatedOwner owner,
            LinkTrafficWindow window,
            AnalyticsRange range,
            String tag,
            String lifecycle,
            int limit);

    AnalyticsFreshness getAnalyticsFreshness(AuthenticatedOwner owner);

    AnalyticsFreshness getAnalyticsFreshness(AuthenticatedOwner owner, String slug);

    record BulkLinkActionResult(
            String slug,
            boolean success,
            Long newVersion,
            String errorCategory,
            String errorDetail) {
    }
}
