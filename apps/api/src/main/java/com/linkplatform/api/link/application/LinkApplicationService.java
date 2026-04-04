package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import java.util.List;

public interface LinkApplicationService {

    LinkMutationResult createLink(CreateLinkCommand command, String idempotencyKey);

    LinkMutationResult updateLink(
            String slug,
            String originalUrl,
            java.time.OffsetDateTime expiresAt,
            String title,
            List<String> tags,
            long expectedVersion,
            String idempotencyKey);

    LinkMutationResult deleteLink(String slug, long expectedVersion, String idempotencyKey);

    Link resolveLink(String slug);

    void recordRedirectClick(String slug, String userAgent, String referrer, String remoteAddress);

    LinkDetails getLink(String slug);

    List<LinkDetails> listRecentLinks(int limit, String query, LinkLifecycleState state);

    List<LinkSuggestion> suggestLinks(String query, int limit);

    List<LinkActivityEvent> getRecentActivity(int limit);

    LinkTrafficSummary getTrafficSummary(String slug);

    List<TopLinkTraffic> getTopLinks(LinkTrafficWindow window);

    List<TrendingLink> getTrendingLinks(LinkTrafficWindow window, int limit);
}
