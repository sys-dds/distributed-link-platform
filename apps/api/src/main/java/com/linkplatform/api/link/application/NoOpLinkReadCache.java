package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "link-platform.cache", name = "enabled", havingValue = "false")
public class NoOpLinkReadCache implements LinkReadCache {

    @Override public Optional<Link> getPublicRedirect(String slug) { return Optional.empty(); }
    @Override public void putPublicRedirect(String slug, Link link) { }
    @Override public void invalidatePublicRedirect(String slug) { }
    @Override public Optional<LinkDetails> getOwnerLinkDetails(long ownerId, String slug) { return Optional.empty(); }
    @Override public void putOwnerLinkDetails(long ownerId, String slug, LinkDetails linkDetails) { }
    @Override public Optional<List<LinkDetails>> getOwnerRecentLinks(long ownerId, int limit, String query, LinkLifecycleState state) { return Optional.empty(); }
    @Override public void putOwnerRecentLinks(long ownerId, int limit, String query, LinkLifecycleState state, List<LinkDetails> linkDetails) { }
    @Override public Optional<List<LinkSuggestion>> getOwnerSuggestions(long ownerId, String query, int limit) { return Optional.empty(); }
    @Override public void putOwnerSuggestions(long ownerId, String query, int limit, List<LinkSuggestion> suggestions) { }
    @Override public Optional<LinkDiscoveryPage> getOwnerDiscoveryPage(long ownerId, LinkDiscoveryQuery query) { return Optional.empty(); }
    @Override public void putOwnerDiscoveryPage(long ownerId, LinkDiscoveryQuery query, LinkDiscoveryPage page) { }
    @Override public Optional<List<LinkActivityEvent>> getOwnerRecentActivity(long ownerId, int limit) { return Optional.empty(); }
    @Override public void putOwnerRecentActivity(long ownerId, int limit, List<LinkActivityEvent> activityEvents) { }
    @Override public Optional<LinkTrafficSummary> getOwnerTrafficSummary(long ownerId, String slug) { return Optional.empty(); }
    @Override public void putOwnerTrafficSummary(long ownerId, String slug, LinkTrafficSummary summary) { }
    @Override public Optional<List<TopLinkTraffic>> getOwnerTopLinks(long ownerId, LinkTrafficWindow window) { return Optional.empty(); }
    @Override public void putOwnerTopLinks(long ownerId, LinkTrafficWindow window, List<TopLinkTraffic> topLinks) { }
    @Override public Optional<List<TrendingLink>> getOwnerTrendingLinks(long ownerId, LinkTrafficWindow window, int limit) { return Optional.empty(); }
    @Override public void putOwnerTrendingLinks(long ownerId, LinkTrafficWindow window, int limit, List<TrendingLink> trendingLinks) { }
    @Override public void invalidateOwnerControlPlane(long ownerId) { }
    @Override public void invalidateOwnerAnalytics(long ownerId) { }
}
