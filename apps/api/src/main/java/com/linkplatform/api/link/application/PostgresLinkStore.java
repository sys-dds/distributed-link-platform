package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import com.linkplatform.api.link.domain.LinkSlug;
import com.linkplatform.api.link.domain.OriginalUrl;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PostgresLinkStore implements LinkStore {

    private final JdbcTemplate jdbcTemplate;

    public PostgresLinkStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean save(Link link, OffsetDateTime expiresAt) {
        try {
            return jdbcTemplate.update(
                    "INSERT INTO links (slug, original_url, expires_at) VALUES (?, ?, ?)",
                    link.slug().value(),
                    link.originalUrl().value(),
                    expiresAt) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public boolean update(String slug, String originalUrl, OffsetDateTime expiresAt) {
        return jdbcTemplate.update(
                "UPDATE links SET original_url = ?, expires_at = ? WHERE slug = ?",
                originalUrl,
                expiresAt,
                slug) == 1;
    }

    @Override
    public boolean deleteBySlug(String slug) {
        return jdbcTemplate.update("DELETE FROM links WHERE slug = ?", slug) == 1;
    }

    @Override
    public void recordClick(LinkClick linkClick) {
        jdbcTemplate.update(
                """
                INSERT INTO link_clicks (slug, clicked_at, user_agent, referrer, remote_address)
                VALUES (?, ?, ?, ?, ?)
                """,
                linkClick.slug(),
                linkClick.clickedAt(),
                linkClick.userAgent(),
                linkClick.referrer(),
                linkClick.remoteAddress());

        incrementDailyRollup(linkClick.slug(), linkClick.clickedAt().toLocalDate());
    }

    @Override
    public Optional<Link> findBySlug(String slug, OffsetDateTime now) {
        return jdbcTemplate.query(
                        """
                        SELECT slug, original_url
                        FROM links
                        WHERE slug = ?
                          AND (expires_at IS NULL OR expires_at > ?)
                        """,
                        (resultSet, rowNum) -> toLink(resultSet.getString("slug"), resultSet.getString("original_url")),
                        slug,
                        now)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<LinkDetails> findDetailsBySlug(String slug, OffsetDateTime now) {
        return jdbcTemplate.query(
                        """
                        SELECT l.slug,
                               l.original_url,
                               l.created_at,
                               l.expires_at,
                               COALESCE((SELECT SUM(r.click_count) FROM link_click_daily_rollups r WHERE r.slug = l.slug), 0) AS click_total
                        FROM links l
                        WHERE l.slug = ?
                          AND (expires_at IS NULL OR expires_at > ?)
                        """,
                        (resultSet, rowNum) -> toLinkDetails(
                                resultSet.getString("slug"),
                                resultSet.getString("original_url"),
                                resultSet.getObject("created_at", OffsetDateTime.class),
                                resultSet.getObject("expires_at", OffsetDateTime.class),
                                resultSet.getLong("click_total")),
                        slug,
                        now)
                .stream()
                .findFirst();
    }

    @Override
    public List<LinkDetails> findRecent(int limit, OffsetDateTime now, String query, LinkLifecycleState state) {
        StringBuilder sql = new StringBuilder("""
                SELECT l.slug,
                       l.original_url,
                       l.created_at,
                       l.expires_at,
                       COALESCE((SELECT SUM(r.click_count) FROM link_click_daily_rollups r WHERE r.slug = l.slug), 0) AS click_total
                FROM links l
                WHERE 1 = 1
                """);
        List<Object> parameters = new ArrayList<>();

        appendLifecycleClause(sql, parameters, now, state);
        appendSearchClause(sql, parameters, query);

        sql.append("""
                
                ORDER BY created_at DESC, slug ASC
                LIMIT ?
                """);
        parameters.add(limit);

        return jdbcTemplate.query(
                sql.toString(),
                (resultSet, rowNum) -> toLinkDetails(
                        resultSet.getString("slug"),
                        resultSet.getString("original_url"),
                        resultSet.getObject("created_at", OffsetDateTime.class),
                        resultSet.getObject("expires_at", OffsetDateTime.class),
                        resultSet.getLong("click_total")),
                parameters.toArray());
    }

    @Override
    public Optional<LinkTrafficSummaryTotals> findTrafficSummaryTotals(
            String slug,
            OffsetDateTime last24HoursSince,
            LocalDate last7DaysStartDate) {
        return jdbcTemplate.query(
                        """
                        SELECT l.slug,
                               l.original_url,
                               COALESCE((SELECT SUM(r.click_count)
                                         FROM link_click_daily_rollups r
                                         WHERE r.slug = l.slug), 0) AS total_clicks,
                               COALESCE((SELECT COUNT(*)
                                         FROM link_clicks c
                                         WHERE c.slug = l.slug
                                           AND c.clicked_at >= ?), 0) AS clicks_last_24_hours,
                               COALESCE((SELECT SUM(r.click_count)
                                         FROM link_click_daily_rollups r
                                         WHERE r.slug = l.slug
                                           AND r.rollup_date >= ?), 0) AS clicks_last_7_days
                        FROM links l
                        WHERE l.slug = ?
                        """,
                        (resultSet, rowNum) -> new LinkTrafficSummaryTotals(
                                resultSet.getString("slug"),
                                resultSet.getString("original_url"),
                                resultSet.getLong("total_clicks"),
                                resultSet.getLong("clicks_last_24_hours"),
                                resultSet.getLong("clicks_last_7_days")),
                        last24HoursSince,
                        last7DaysStartDate,
                        slug)
                .stream()
                .findFirst();
    }

    @Override
    public List<DailyClickBucket> findRecentDailyClickBuckets(String slug, LocalDate startDate) {
        return jdbcTemplate.query(
                """
                SELECT rollup_date, click_count
                FROM link_click_daily_rollups
                WHERE slug = ?
                  AND rollup_date >= ?
                ORDER BY rollup_date ASC
                """,
                (resultSet, rowNum) -> new DailyClickBucket(
                        resultSet.getObject("rollup_date", LocalDate.class),
                        resultSet.getLong("click_count")),
                slug,
                startDate);
    }

    @Override
    public List<TopLinkTraffic> findTopLinks(LinkTrafficWindow window, OffsetDateTime now) {
        return switch (window) {
            case LAST_24_HOURS -> findTopLinksLast24Hours(now.minusHours(24));
            case LAST_7_DAYS -> findTopLinksLast7Days(now.toLocalDate().minusDays(6));
        };
    }

    private void appendLifecycleClause(
            StringBuilder sql,
            List<Object> parameters,
            OffsetDateTime now,
            LinkLifecycleState state) {
        switch (state) {
            case ACTIVE -> {
                sql.append("""
                        
                          AND (expires_at IS NULL OR expires_at > ?)
                        """);
                parameters.add(now);
            }
            case EXPIRED -> {
                sql.append("""
                        
                          AND expires_at IS NOT NULL
                          AND expires_at <= ?
                        """);
                parameters.add(now);
            }
            case ALL -> {
                // no lifecycle filter
            }
        }
    }

    private void appendSearchClause(StringBuilder sql, List<Object> parameters, String query) {
        if (query == null || query.isBlank()) {
            return;
        }

        sql.append("""
                
                  AND (
                    LOWER(l.slug) LIKE ?
                    OR LOWER(l.original_url) LIKE ?
                  )
                """);
        String pattern = "%" + query.toLowerCase(Locale.ROOT).trim() + "%";
        parameters.add(pattern);
        parameters.add(pattern);
    }

    private void incrementDailyRollup(String slug, LocalDate rollupDate) {
        int updated = jdbcTemplate.update(
                """
                UPDATE link_click_daily_rollups
                SET click_count = click_count + 1
                WHERE slug = ? AND rollup_date = ?
                """,
                slug,
                rollupDate);
        if (updated == 1) {
            return;
        }

        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO link_click_daily_rollups (slug, rollup_date, click_count)
                    VALUES (?, ?, 1)
                    """,
                    slug,
                    rollupDate);
        } catch (DuplicateKeyException exception) {
            jdbcTemplate.update(
                    """
                    UPDATE link_click_daily_rollups
                    SET click_count = click_count + 1
                    WHERE slug = ? AND rollup_date = ?
                    """,
                    slug,
                    rollupDate);
        }
    }

    private List<TopLinkTraffic> findTopLinksLast24Hours(OffsetDateTime since) {
        return jdbcTemplate.query(
                """
                SELECT l.slug, l.original_url, COUNT(*) AS click_total
                FROM links l
                JOIN link_clicks c ON c.slug = l.slug
                WHERE c.clicked_at >= ?
                GROUP BY l.slug, l.original_url
                ORDER BY click_total DESC, l.slug ASC
                """,
                (resultSet, rowNum) -> new TopLinkTraffic(
                        resultSet.getString("slug"),
                        resultSet.getString("original_url"),
                        resultSet.getLong("click_total")),
                since);
    }

    private List<TopLinkTraffic> findTopLinksLast7Days(LocalDate startDate) {
        return jdbcTemplate.query(
                """
                SELECT l.slug, l.original_url, SUM(r.click_count) AS click_total
                FROM links l
                JOIN link_click_daily_rollups r ON r.slug = l.slug
                WHERE r.rollup_date >= ?
                GROUP BY l.slug, l.original_url
                ORDER BY click_total DESC, l.slug ASC
                """,
                (resultSet, rowNum) -> new TopLinkTraffic(
                        resultSet.getString("slug"),
                        resultSet.getString("original_url"),
                        resultSet.getLong("click_total")),
                startDate);
    }

    private Link toLink(String slug, String originalUrl) {
        return new Link(new LinkSlug(slug), new OriginalUrl(originalUrl));
    }

    private LinkDetails toLinkDetails(
            String slug,
            String originalUrl,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt,
            long clickTotal) {
        return new LinkDetails(slug, originalUrl, createdAt, expiresAt, clickTotal);
    }
}
