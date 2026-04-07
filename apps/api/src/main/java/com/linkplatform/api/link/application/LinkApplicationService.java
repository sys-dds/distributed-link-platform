package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import com.linkplatform.api.owner.application.AuthenticatedOwner;
import com.linkplatform.api.owner.application.WorkspaceAccessContext;
import com.linkplatform.api.owner.application.WorkspaceRole;
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

    LinkMutationResult createLink(WorkspaceAccessContext context, CreateLinkCommand command, String idempotencyKey);

    LinkMutationResult updateLink(
            WorkspaceAccessContext context,
            String slug,
            String originalUrl,
            java.time.OffsetDateTime expiresAt,
            String title,
            List<String> tags,
            long expectedVersion,
            String idempotencyKey);

    LinkMutationResult deleteLink(WorkspaceAccessContext context, String slug, long expectedVersion, String idempotencyKey);

    LinkMutationResult changeLifecycle(
            WorkspaceAccessContext context,
            String slug,
            String action,
            OffsetDateTime expiresAt,
            long expectedVersion,
            String idempotencyKey);

    List<BulkLinkActionResult> bulkAction(
            WorkspaceAccessContext context,
            String action,
            List<String> slugs,
            List<String> tags,
            OffsetDateTime expiresAt,
            String idempotencyKey);

    Link resolveLink(String slug);

    void recordRedirectClick(String slug, String userAgent, String referrer, String remoteAddress);

    LinkDetails getLink(WorkspaceAccessContext context, String slug);

    List<LinkDetails> listRecentLinks(
            WorkspaceAccessContext context,
            int limit,
            String query,
            LinkLifecycleState state,
            LinkAbuseStatus abuseStatus);

    default List<LinkDetails> listRecentLinks(WorkspaceAccessContext context, int limit, String query, LinkLifecycleState state) {
        return listRecentLinks(context, limit, query, state, null);
    }

    List<LinkSuggestion> suggestLinks(WorkspaceAccessContext context, String query, int limit);

    LinkDiscoveryPage searchLinks(WorkspaceAccessContext context, LinkDiscoveryQuery query);

    long countActiveLinks(WorkspaceAccessContext context);

    List<LinkActivityEvent> getRecentActivity(WorkspaceAccessContext context, int limit);

    List<LinkActivityEvent> getRecentActivity(WorkspaceAccessContext context, int limit, String tag, String lifecycle);

    LinkTrafficSummary getTrafficSummary(WorkspaceAccessContext context, String slug);

    AnalyticsSummaryView getTrafficSummary(WorkspaceAccessContext context, String slug, AnalyticsRange range);

    LinkTrafficSeriesView getTrafficSeries(
            WorkspaceAccessContext context,
            String slug,
            AnalyticsRange range,
            String granularity);

    List<TopLinkTraffic> getTopLinks(WorkspaceAccessContext context, LinkTrafficWindow window);

    List<TopLinkTraffic> getTopLinks(
            WorkspaceAccessContext context,
            LinkTrafficWindow window,
            AnalyticsRange range,
            String tag,
            String lifecycle,
            int limit);

    List<TrendingLink> getTrendingLinks(WorkspaceAccessContext context, LinkTrafficWindow window, int limit);

    List<TrendingLink> getTrendingLinks(
            WorkspaceAccessContext context,
            LinkTrafficWindow window,
            AnalyticsRange range,
            String tag,
            String lifecycle,
            int limit);

    AnalyticsFreshness getAnalyticsFreshness(WorkspaceAccessContext context);

    AnalyticsFreshness getAnalyticsFreshness(WorkspaceAccessContext context, String slug);

    record BulkLinkActionResult(
            String slug,
            boolean success,
            Long newVersion,
            String errorCategory,
            String errorDetail) {
    }

    default LinkMutationResult createLink(AuthenticatedOwner owner, CreateLinkCommand command, String idempotencyKey) {
        return createLink(compatibilityContext(owner), command, idempotencyKey);
    }

    default LinkMutationResult updateLink(
            AuthenticatedOwner owner,
            String slug,
            String originalUrl,
            OffsetDateTime expiresAt,
            String title,
            List<String> tags,
            long expectedVersion,
            String idempotencyKey) {
        return updateLink(compatibilityContext(owner), slug, originalUrl, expiresAt, title, tags, expectedVersion, idempotencyKey);
    }

    default LinkMutationResult deleteLink(AuthenticatedOwner owner, String slug, long expectedVersion, String idempotencyKey) {
        return deleteLink(compatibilityContext(owner), slug, expectedVersion, idempotencyKey);
    }

    default LinkMutationResult changeLifecycle(
            AuthenticatedOwner owner,
            String slug,
            String action,
            OffsetDateTime expiresAt,
            long expectedVersion,
            String idempotencyKey) {
        return changeLifecycle(compatibilityContext(owner), slug, action, expiresAt, expectedVersion, idempotencyKey);
    }

    default List<BulkLinkActionResult> bulkAction(
            AuthenticatedOwner owner,
            String action,
            List<String> slugs,
            List<String> tags,
            OffsetDateTime expiresAt,
            String idempotencyKey) {
        return bulkAction(compatibilityContext(owner), action, slugs, tags, expiresAt, idempotencyKey);
    }

    default LinkDetails getLink(AuthenticatedOwner owner, String slug) {
        return getLink(compatibilityContext(owner), slug);
    }

    default List<LinkDetails> listRecentLinks(
            AuthenticatedOwner owner,
            int limit,
            String query,
            LinkLifecycleState state,
            LinkAbuseStatus abuseStatus) {
        return listRecentLinks(compatibilityContext(owner), limit, query, state, abuseStatus);
    }

    default List<LinkDetails> listRecentLinks(AuthenticatedOwner owner, int limit, String query, LinkLifecycleState state) {
        return listRecentLinks(owner, limit, query, state, null);
    }

    default List<LinkSuggestion> suggestLinks(AuthenticatedOwner owner, String query, int limit) {
        return suggestLinks(compatibilityContext(owner), query, limit);
    }

    default LinkDiscoveryPage searchLinks(AuthenticatedOwner owner, LinkDiscoveryQuery query) {
        return searchLinks(compatibilityContext(owner), query);
    }

    default long countActiveLinks(AuthenticatedOwner owner) {
        return countActiveLinks(compatibilityContext(owner));
    }

    default List<LinkActivityEvent> getRecentActivity(AuthenticatedOwner owner, int limit) {
        return getRecentActivity(compatibilityContext(owner), limit);
    }

    default List<LinkActivityEvent> getRecentActivity(AuthenticatedOwner owner, int limit, String tag, String lifecycle) {
        return getRecentActivity(compatibilityContext(owner), limit, tag, lifecycle);
    }

    default LinkTrafficSummary getTrafficSummary(AuthenticatedOwner owner, String slug) {
        return getTrafficSummary(compatibilityContext(owner), slug);
    }

    default AnalyticsSummaryView getTrafficSummary(AuthenticatedOwner owner, String slug, AnalyticsRange range) {
        return getTrafficSummary(compatibilityContext(owner), slug, range);
    }

    default LinkTrafficSeriesView getTrafficSeries(
            AuthenticatedOwner owner,
            String slug,
            AnalyticsRange range,
            String granularity) {
        return getTrafficSeries(compatibilityContext(owner), slug, range, granularity);
    }

    default List<TopLinkTraffic> getTopLinks(AuthenticatedOwner owner, LinkTrafficWindow window) {
        return getTopLinks(compatibilityContext(owner), window);
    }

    default List<TopLinkTraffic> getTopLinks(
            AuthenticatedOwner owner,
            LinkTrafficWindow window,
            AnalyticsRange range,
            String tag,
            String lifecycle,
            int limit) {
        return getTopLinks(compatibilityContext(owner), window, range, tag, lifecycle, limit);
    }

    default List<TrendingLink> getTrendingLinks(AuthenticatedOwner owner, LinkTrafficWindow window, int limit) {
        return getTrendingLinks(compatibilityContext(owner), window, limit);
    }

    default List<TrendingLink> getTrendingLinks(
            AuthenticatedOwner owner,
            LinkTrafficWindow window,
            AnalyticsRange range,
            String tag,
            String lifecycle,
            int limit) {
        return getTrendingLinks(compatibilityContext(owner), window, range, tag, lifecycle, limit);
    }

    default AnalyticsFreshness getAnalyticsFreshness(AuthenticatedOwner owner) {
        return getAnalyticsFreshness(compatibilityContext(owner));
    }

    default AnalyticsFreshness getAnalyticsFreshness(AuthenticatedOwner owner, String slug) {
        return getAnalyticsFreshness(compatibilityContext(owner), slug);
    }

    private static WorkspaceAccessContext compatibilityContext(AuthenticatedOwner owner) {
        return new WorkspaceAccessContext(
                owner,
                owner.id(),
                owner.ownerKey(),
                owner.displayName(),
                true,
                WorkspaceRole.OWNER,
                WorkspaceRole.OWNER.impliedScopes(),
                null);
    }
}
