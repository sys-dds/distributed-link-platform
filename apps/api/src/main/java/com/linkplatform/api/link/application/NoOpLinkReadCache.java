package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "link-platform.cache", name = "enabled", havingValue = "false")
public class NoOpLinkReadCache implements LinkReadCache {

    @Override public long getPublicRedirectGeneration(String slug) { return 0L; }
    @Override public Optional<Link> getPublicRedirect(String slug, long generation) { return Optional.empty(); }
    @Override public PublicRedirectLookupResult lookupPublicRedirect(String slug) { return new PublicRedirectLookupResult(PublicRedirectLookupOutcome.MISS, 0L, Optional.empty()); }
    @Override public void putPublicRedirect(String slug, long generation, Link link) { }
    @Override public void invalidatePublicRedirect(String slug) { }
    @Override public long getOwnerControlPlaneGeneration(long ownerId) { return 0L; }
    @Override public Optional<LinkDetails> getOwnerLinkDetails(long ownerId, long generation, String slug) { return Optional.empty(); }
    @Override public void putOwnerLinkDetails(long ownerId, long generation, String slug, LinkDetails linkDetails) { }
    @Override public Optional<List<LinkDetails>> getOwnerRecentLinks(long ownerId, long generation, int limit, String query, LinkLifecycleState state) { return Optional.empty(); }
    @Override public void putOwnerRecentLinks(long ownerId, long generation, int limit, String query, LinkLifecycleState state, List<LinkDetails> linkDetails) { }
    @Override public Optional<List<LinkSuggestion>> getOwnerSuggestions(long ownerId, long generation, String query, int limit) { return Optional.empty(); }
    @Override public void putOwnerSuggestions(long ownerId, long generation, String query, int limit, List<LinkSuggestion> suggestions) { }
    @Override public Optional<LinkDiscoveryPage> getOwnerDiscoveryPage(long ownerId, long generation, LinkDiscoveryQuery query) { return Optional.empty(); }
    @Override public void putOwnerDiscoveryPage(long ownerId, long generation, LinkDiscoveryQuery query, LinkDiscoveryPage page) { }
    @Override public long getOwnerAnalyticsGeneration(long ownerId) { return 0L; }
    @Override public Optional<List<LinkActivityEvent>> getOwnerRecentActivity(long ownerId, long generation, int limit) { return Optional.empty(); }
    @Override public void putOwnerRecentActivity(long ownerId, long generation, int limit, List<LinkActivityEvent> activityEvents) { }
    @Override public Optional<LinkTrafficSummary> getOwnerTrafficSummary(long ownerId, long generation, String slug) { return Optional.empty(); }
    @Override public void putOwnerTrafficSummary(long ownerId, long generation, String slug, LinkTrafficSummary summary) { }
    @Override public Optional<List<TopLinkTraffic>> getOwnerTopLinks(long ownerId, long generation, LinkTrafficWindow window) { return Optional.empty(); }
    @Override public void putOwnerTopLinks(long ownerId, long generation, LinkTrafficWindow window, List<TopLinkTraffic> topLinks) { }
    @Override public Optional<List<TrendingLink>> getOwnerTrendingLinks(long ownerId, long generation, LinkTrafficWindow window, int limit) { return Optional.empty(); }
    @Override public void putOwnerTrendingLinks(long ownerId, long generation, LinkTrafficWindow window, int limit, List<TrendingLink> trendingLinks) { }
    @Override public void invalidateOwnerControlPlane(long ownerId) { }
    @Override public void invalidateOwnerAnalytics(long ownerId) { }
}
