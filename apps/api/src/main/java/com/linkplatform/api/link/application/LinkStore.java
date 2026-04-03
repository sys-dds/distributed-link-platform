package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface LinkStore {

    boolean save(Link link, OffsetDateTime expiresAt, String title, List<String> tags, String hostname);

    boolean update(String slug, String originalUrl, OffsetDateTime expiresAt, String title, List<String> tags, String hostname);

    boolean deleteBySlug(String slug);

    void recordClick(LinkClick linkClick);

    Optional<Link> findBySlug(String slug, OffsetDateTime now);

    Optional<LinkDetails> findDetailsBySlug(String slug, OffsetDateTime now);

    List<LinkDetails> findRecent(int limit, OffsetDateTime now, String query, LinkLifecycleState state);

    List<LinkSuggestion> findSuggestions(int limit, OffsetDateTime now, String query);

    Optional<LinkTrafficSummaryTotals> findTrafficSummaryTotals(
            String slug,
            OffsetDateTime last24HoursSince,
            java.time.LocalDate last7DaysStartDate);

    List<DailyClickBucket> findRecentDailyClickBuckets(String slug, java.time.LocalDate startDate);

    List<TopLinkTraffic> findTopLinks(LinkTrafficWindow window, OffsetDateTime now);
}
