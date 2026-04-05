package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import com.linkplatform.api.owner.application.AuthenticatedOwner;
import java.util.List;

public interface LinkApplicationService {

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

    Link resolveLink(String slug);

    void recordRedirectClick(String slug, String userAgent, String referrer, String remoteAddress);

    LinkDetails getLink(AuthenticatedOwner owner, String slug);

    List<LinkDetails> listRecentLinks(AuthenticatedOwner owner, int limit, String query, LinkLifecycleState state);

    List<LinkSuggestion> suggestLinks(AuthenticatedOwner owner, String query, int limit);

    long countActiveLinks(AuthenticatedOwner owner);

    List<LinkActivityEvent> getRecentActivity(int limit);

    LinkTrafficSummary getTrafficSummary(String slug);

    List<TopLinkTraffic> getTopLinks(LinkTrafficWindow window);

    List<TrendingLink> getTrendingLinks(LinkTrafficWindow window, int limit);
}
