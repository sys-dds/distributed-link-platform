package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import java.util.List;
import java.util.Optional;

public interface LinkReadCache {

    Optional<Link> getPublicRedirect(String slug);

    void putPublicRedirect(String slug, Link link);

    void invalidatePublicRedirect(String slug);

    Optional<LinkDetails> getOwnerLinkDetails(long ownerId, String slug);

    void putOwnerLinkDetails(long ownerId, String slug, LinkDetails linkDetails);

    Optional<List<LinkDetails>> getOwnerRecentLinks(long ownerId, int limit, String query, LinkLifecycleState state);

    void putOwnerRecentLinks(long ownerId, int limit, String query, LinkLifecycleState state, List<LinkDetails> linkDetails);

    Optional<List<LinkSuggestion>> getOwnerSuggestions(long ownerId, String query, int limit);

    void putOwnerSuggestions(long ownerId, String query, int limit, List<LinkSuggestion> suggestions);

    Optional<List<LinkActivityEvent>> getOwnerRecentActivity(long ownerId, int limit);

    void putOwnerRecentActivity(long ownerId, int limit, List<LinkActivityEvent> activityEvents);

    Optional<LinkTrafficSummary> getOwnerTrafficSummary(long ownerId, String slug);

    void putOwnerTrafficSummary(long ownerId, String slug, LinkTrafficSummary summary);

    Optional<List<TopLinkTraffic>> getOwnerTopLinks(long ownerId, LinkTrafficWindow window);

    void putOwnerTopLinks(long ownerId, LinkTrafficWindow window, List<TopLinkTraffic> topLinks);

    Optional<List<TrendingLink>> getOwnerTrendingLinks(long ownerId, LinkTrafficWindow window, int limit);

    void putOwnerTrendingLinks(long ownerId, LinkTrafficWindow window, int limit, List<TrendingLink> trendingLinks);

    void invalidateOwnerControlPlane(long ownerId);

    void invalidateOwnerAnalytics(long ownerId);
}
