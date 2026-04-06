package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import java.util.List;
import java.util.Optional;

public interface LinkReadCache {

    long CACHE_UNAVAILABLE_GENERATION = -1L;

    long getPublicRedirectGeneration(String slug);

    Optional<Link> getPublicRedirect(String slug, long generation);

    default PublicRedirectLookupResult lookupPublicRedirect(String slug) {
        long generation = getPublicRedirectGeneration(slug);
        if (!isCacheGenerationAvailable(generation)) {
            return new PublicRedirectLookupResult(PublicRedirectLookupOutcome.GENERATION_UNAVAILABLE, generation, Optional.empty());
        }
        return getPublicRedirect(slug, generation)
                .map(link -> new PublicRedirectLookupResult(PublicRedirectLookupOutcome.HIT, generation, Optional.of(link)))
                .orElseGet(() -> new PublicRedirectLookupResult(PublicRedirectLookupOutcome.MISS, generation, Optional.empty()));
    }

    default Optional<Link> getPublicRedirect(String slug) {
        return getPublicRedirect(slug, getPublicRedirectGeneration(slug));
    }

    void putPublicRedirect(String slug, long generation, Link link);

    default void putPublicRedirect(String slug, Link link) {
        putPublicRedirect(slug, getPublicRedirectGeneration(slug), link);
    }

    void invalidatePublicRedirect(String slug);

    long getOwnerControlPlaneGeneration(long ownerId);

    Optional<LinkDetails> getOwnerLinkDetails(long ownerId, long generation, String slug);

    default Optional<LinkDetails> getOwnerLinkDetails(long ownerId, String slug) {
        return getOwnerLinkDetails(ownerId, getOwnerControlPlaneGeneration(ownerId), slug);
    }

    void putOwnerLinkDetails(long ownerId, long generation, String slug, LinkDetails linkDetails);

    default void putOwnerLinkDetails(long ownerId, String slug, LinkDetails linkDetails) {
        putOwnerLinkDetails(ownerId, getOwnerControlPlaneGeneration(ownerId), slug, linkDetails);
    }

    Optional<List<LinkDetails>> getOwnerRecentLinks(long ownerId, long generation, int limit, String query, LinkLifecycleState state);

    default Optional<List<LinkDetails>> getOwnerRecentLinks(long ownerId, int limit, String query, LinkLifecycleState state) {
        return getOwnerRecentLinks(ownerId, getOwnerControlPlaneGeneration(ownerId), limit, query, state);
    }

    void putOwnerRecentLinks(long ownerId, long generation, int limit, String query, LinkLifecycleState state, List<LinkDetails> linkDetails);

    default void putOwnerRecentLinks(long ownerId, int limit, String query, LinkLifecycleState state, List<LinkDetails> linkDetails) {
        putOwnerRecentLinks(ownerId, getOwnerControlPlaneGeneration(ownerId), limit, query, state, linkDetails);
    }

    Optional<List<LinkSuggestion>> getOwnerSuggestions(long ownerId, long generation, String query, int limit);

    default Optional<List<LinkSuggestion>> getOwnerSuggestions(long ownerId, String query, int limit) {
        return getOwnerSuggestions(ownerId, getOwnerControlPlaneGeneration(ownerId), query, limit);
    }

    void putOwnerSuggestions(long ownerId, long generation, String query, int limit, List<LinkSuggestion> suggestions);

    default void putOwnerSuggestions(long ownerId, String query, int limit, List<LinkSuggestion> suggestions) {
        putOwnerSuggestions(ownerId, getOwnerControlPlaneGeneration(ownerId), query, limit, suggestions);
    }

    Optional<LinkDiscoveryPage> getOwnerDiscoveryPage(long ownerId, long generation, LinkDiscoveryQuery query);

    default Optional<LinkDiscoveryPage> getOwnerDiscoveryPage(long ownerId, LinkDiscoveryQuery query) {
        return getOwnerDiscoveryPage(ownerId, getOwnerControlPlaneGeneration(ownerId), query);
    }

    void putOwnerDiscoveryPage(long ownerId, long generation, LinkDiscoveryQuery query, LinkDiscoveryPage page);

    default void putOwnerDiscoveryPage(long ownerId, LinkDiscoveryQuery query, LinkDiscoveryPage page) {
        putOwnerDiscoveryPage(ownerId, getOwnerControlPlaneGeneration(ownerId), query, page);
    }

    long getOwnerAnalyticsGeneration(long ownerId);

    Optional<List<LinkActivityEvent>> getOwnerRecentActivity(long ownerId, long generation, int limit);

    default Optional<List<LinkActivityEvent>> getOwnerRecentActivity(long ownerId, int limit) {
        return getOwnerRecentActivity(ownerId, getOwnerAnalyticsGeneration(ownerId), limit);
    }

    void putOwnerRecentActivity(long ownerId, long generation, int limit, List<LinkActivityEvent> activityEvents);

    default void putOwnerRecentActivity(long ownerId, int limit, List<LinkActivityEvent> activityEvents) {
        putOwnerRecentActivity(ownerId, getOwnerAnalyticsGeneration(ownerId), limit, activityEvents);
    }

    Optional<LinkTrafficSummary> getOwnerTrafficSummary(long ownerId, long generation, String slug);

    default Optional<LinkTrafficSummary> getOwnerTrafficSummary(long ownerId, String slug) {
        return getOwnerTrafficSummary(ownerId, getOwnerAnalyticsGeneration(ownerId), slug);
    }

    void putOwnerTrafficSummary(long ownerId, long generation, String slug, LinkTrafficSummary summary);

    default void putOwnerTrafficSummary(long ownerId, String slug, LinkTrafficSummary summary) {
        putOwnerTrafficSummary(ownerId, getOwnerAnalyticsGeneration(ownerId), slug, summary);
    }

    Optional<List<TopLinkTraffic>> getOwnerTopLinks(long ownerId, long generation, LinkTrafficWindow window);

    default Optional<List<TopLinkTraffic>> getOwnerTopLinks(long ownerId, LinkTrafficWindow window) {
        return getOwnerTopLinks(ownerId, getOwnerAnalyticsGeneration(ownerId), window);
    }

    void putOwnerTopLinks(long ownerId, long generation, LinkTrafficWindow window, List<TopLinkTraffic> topLinks);

    default void putOwnerTopLinks(long ownerId, LinkTrafficWindow window, List<TopLinkTraffic> topLinks) {
        putOwnerTopLinks(ownerId, getOwnerAnalyticsGeneration(ownerId), window, topLinks);
    }

    Optional<List<TrendingLink>> getOwnerTrendingLinks(long ownerId, long generation, LinkTrafficWindow window, int limit);

    default Optional<List<TrendingLink>> getOwnerTrendingLinks(long ownerId, LinkTrafficWindow window, int limit) {
        return getOwnerTrendingLinks(ownerId, getOwnerAnalyticsGeneration(ownerId), window, limit);
    }

    void putOwnerTrendingLinks(long ownerId, long generation, LinkTrafficWindow window, int limit, List<TrendingLink> trendingLinks);

    default void putOwnerTrendingLinks(long ownerId, LinkTrafficWindow window, int limit, List<TrendingLink> trendingLinks) {
        putOwnerTrendingLinks(ownerId, getOwnerAnalyticsGeneration(ownerId), window, limit, trendingLinks);
    }

    void invalidateOwnerControlPlane(long ownerId);

    void invalidateOwnerAnalytics(long ownerId);

    default boolean isCacheGenerationAvailable(long generation) {
        return generation >= 0;
    }

    enum PublicRedirectLookupOutcome {
        HIT,
        MISS,
        GENERATION_UNAVAILABLE,
        DEGRADED
    }

    record PublicRedirectLookupResult(
            PublicRedirectLookupOutcome outcome,
            long generation,
            Optional<Link> link) {
    }
}
