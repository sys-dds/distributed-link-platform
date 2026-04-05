package com.linkplatform.api.link.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final TypeReference<List<String>> TAG_LIST_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PostgresLinkStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean save(
            Link link,
            OffsetDateTime expiresAt,
            String title,
            List<String> tags,
            String hostname,
            long version,
            long ownerId) {
        try {
            return jdbcTemplate.update(
                    """
                    INSERT INTO links (slug, original_url, expires_at, title, tags_json, hostname, version, owner_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    link.slug().value(),
                    link.originalUrl().value(),
                    expiresAt,
                    title,
                    serializeTags(tags),
                    hostname,
                    version,
                    ownerId) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public boolean update(
            String slug,
            String originalUrl,
            OffsetDateTime expiresAt,
            String title,
            List<String> tags,
            String hostname,
            long expectedVersion,
            long nextVersion,
            long ownerId) {
        return jdbcTemplate.update(
                """
                UPDATE links
                SET original_url = ?, expires_at = ?, title = ?, tags_json = ?, hostname = ?, version = ?
                WHERE slug = ?
                  AND version = ?
                  AND owner_id = ?
                """,
                originalUrl,
                expiresAt,
                title,
                serializeTags(tags),
                hostname,
                nextVersion,
                slug,
                expectedVersion,
                ownerId) == 1;
    }

    @Override
    public boolean deleteBySlug(String slug, long expectedVersion, long ownerId) {
        return jdbcTemplate.update(
                        "DELETE FROM links WHERE slug = ? AND version = ? AND owner_id = ?",
                        slug,
                        expectedVersion,
                        ownerId)
                == 1;
    }

    @Override
    public long countActiveLinksByOwner(long ownerId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM links
                WHERE owner_id = ?
                  AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
                """,
                Long.class,
                ownerId);
        return count == null ? 0L : count;
    }

    @Override
    public boolean recordClickIfAbsent(LinkClick linkClick) {
        try {
            jdbcTemplate.update(
                """
                INSERT INTO link_clicks (event_id, slug, clicked_at, user_agent, referrer, remote_address)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                linkClick.eventId(),
                linkClick.slug(),
                linkClick.clickedAt(),
                linkClick.userAgent(),
                linkClick.referrer(),
                linkClick.remoteAddress());
        } catch (DuplicateKeyException exception) {
            return false;
        }

        incrementDailyRollup(linkClick.slug(), linkClick.clickedAt().toLocalDate());
        return true;
    }

    @Override
    public boolean recordActivityIfAbsent(String eventId, LinkActivityEvent linkActivityEvent) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO link_activity_events (
                        event_id, owner_id, event_type, slug, original_url, title, tags_json, hostname, expires_at, occurred_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    eventId,
                    linkActivityEvent.ownerId(),
                    linkActivityEvent.type().name(),
                    linkActivityEvent.slug(),
                    linkActivityEvent.originalUrl(),
                    linkActivityEvent.title(),
                    serializeTags(linkActivityEvent.tags()),
                    linkActivityEvent.hostname(),
                    linkActivityEvent.expiresAt(),
                    linkActivityEvent.occurredAt());
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public Optional<Long> findOwnerIdBySlug(String slug) {
        return jdbcTemplate.query(
                        "SELECT owner_id FROM links WHERE slug = ?",
                        (resultSet, rowNum) -> resultSet.getLong("owner_id"),
                        slug)
                .stream()
                .findFirst();
    }

    @Override
    public long rebuildClickDailyRollups() {
        jdbcTemplate.update("DELETE FROM link_click_daily_rollups");
        return jdbcTemplate.update(
                """
                INSERT INTO link_click_daily_rollups (slug, rollup_date, click_count)
                SELECT slug, CAST(clicked_at AS DATE), COUNT(*)
                FROM link_clicks
                GROUP BY slug, CAST(clicked_at AS DATE)
                """);
    }

    @Override
    public void projectCatalogEvent(LinkLifecycleEvent linkLifecycleEvent) {
        String tagsJson = serializeTags(linkLifecycleEvent.tags());
        switch (linkLifecycleEvent.eventType()) {
            case CREATED, UPDATED, EXPIRATION_UPDATED -> upsertCatalogProjection(
                    linkLifecycleEvent,
                    tagsJson,
                    null);
            case DELETED -> upsertCatalogProjection(
                    linkLifecycleEvent,
                    tagsJson,
                    linkLifecycleEvent.occurredAt());
        }
    }

    @Override
    public void resetCatalogProjection() {
        jdbcTemplate.update("DELETE FROM link_catalog_projection");
    }

    @Override
    public List<LinkClickHistoryRecord> findClickHistoryChunkAfter(long afterId, int limit) {
        return jdbcTemplate.query(
                """
                SELECT id, slug, CAST(clicked_at AS DATE) AS rollup_date
                FROM link_clicks
                WHERE id > ?
                ORDER BY id ASC
                LIMIT ?
                """,
                (resultSet, rowNum) -> new LinkClickHistoryRecord(
                        resultSet.getLong("id"),
                        resultSet.getString("slug"),
                        resultSet.getObject("rollup_date", LocalDate.class)),
                afterId,
                limit);
    }

    @Override
    public long applyClickHistoryChunkToDailyRollups(List<LinkClickHistoryRecord> clickHistoryChunk) {
        if (clickHistoryChunk.isEmpty()) {
            return 0L;
        }

        java.util.Map<String, Long> countsBySlugAndDate = clickHistoryChunk.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        record -> record.slug() + "|" + record.rollupDate(),
                        java.util.stream.Collectors.counting()));
        countsBySlugAndDate.forEach((slugAndDate, increment) -> {
            int delimiterIndex = slugAndDate.indexOf('|');
            String slug = slugAndDate.substring(0, delimiterIndex);
            LocalDate rollupDate = LocalDate.parse(slugAndDate.substring(delimiterIndex + 1));
            upsertDailyRollupCount(slug, rollupDate, increment);
        });
        return clickHistoryChunk.size();
    }

    @Override
    public void resetClickDailyRollups() {
        jdbcTemplate.update("DELETE FROM link_click_daily_rollups");
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
    public Optional<LinkDetails> findDetailsBySlug(String slug, OffsetDateTime now, long ownerId) {
        return jdbcTemplate.query(
                        """
                        SELECT l.slug,
                               l.original_url,
                               l.created_at,
                               l.expires_at,
                               l.title,
                               l.tags_json,
                               l.hostname,
                               l.version,
                               COALESCE((SELECT SUM(r.click_count) FROM link_click_daily_rollups r WHERE r.slug = l.slug), 0) AS click_total
                        FROM links l
                        WHERE l.slug = ?
                          AND l.owner_id = ?
                          AND (expires_at IS NULL OR expires_at > ?)
                        """,
                        (resultSet, rowNum) -> toLinkDetails(
                                resultSet.getString("slug"),
                                resultSet.getString("original_url"),
                                resultSet.getObject("created_at", OffsetDateTime.class),
                                resultSet.getObject("expires_at", OffsetDateTime.class),
                                resultSet.getString("title"),
                                resultSet.getString("tags_json"),
                                resultSet.getString("hostname"),
                                resultSet.getLong("version"),
                                resultSet.getLong("click_total")),
                        slug,
                        ownerId,
                        now)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<LinkDetails> findStoredDetailsBySlug(String slug) {
        return jdbcTemplate.query(
                        """
                        SELECT l.slug,
                               l.original_url,
                               l.created_at,
                               l.expires_at,
                               l.title,
                               l.tags_json,
                               l.hostname,
                               l.version,
                               COALESCE((SELECT SUM(r.click_count) FROM link_click_daily_rollups r WHERE r.slug = l.slug), 0) AS click_total
                        FROM links l
                        WHERE l.slug = ?
                        """,
                        (resultSet, rowNum) -> toLinkDetails(
                                resultSet.getString("slug"),
                                resultSet.getString("original_url"),
                                resultSet.getObject("created_at", OffsetDateTime.class),
                                resultSet.getObject("expires_at", OffsetDateTime.class),
                                resultSet.getString("title"),
                                resultSet.getString("tags_json"),
                                resultSet.getString("hostname"),
                                resultSet.getLong("version"),
                                resultSet.getLong("click_total")),
                        slug)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<LinkDetails> findStoredDetailsBySlug(String slug, long ownerId) {
        return jdbcTemplate.query(
                        """
                        SELECT l.slug,
                               l.original_url,
                               l.created_at,
                               l.expires_at,
                               l.title,
                               l.tags_json,
                               l.hostname,
                               l.version,
                               COALESCE((SELECT SUM(r.click_count) FROM link_click_daily_rollups r WHERE r.slug = l.slug), 0) AS click_total
                        FROM links l
                        WHERE l.slug = ?
                          AND l.owner_id = ?
                        """,
                        (resultSet, rowNum) -> toLinkDetails(
                                resultSet.getString("slug"),
                                resultSet.getString("original_url"),
                                resultSet.getObject("created_at", OffsetDateTime.class),
                                resultSet.getObject("expires_at", OffsetDateTime.class),
                                resultSet.getString("title"),
                                resultSet.getString("tags_json"),
                                resultSet.getString("hostname"),
                                resultSet.getLong("version"),
                                resultSet.getLong("click_total")),
                        slug,
                        ownerId)
                .stream()
                .findFirst();
    }

    @Override
    public List<LinkDetails> findRecent(int limit, OffsetDateTime now, String query, LinkLifecycleState state, long ownerId) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.slug,
                       p.original_url,
                       p.created_at,
                       p.expires_at,
                       p.title,
                       p.tags_json,
                       p.hostname,
                       p.version,
                       COALESCE((SELECT SUM(r.click_count) FROM link_click_daily_rollups r WHERE r.slug = p.slug), 0) AS click_total
                FROM link_catalog_projection p
                WHERE 1 = 1
                  AND p.owner_id = ?
                  AND p.deleted_at IS NULL
                """);
        List<Object> parameters = new ArrayList<>(List.of(ownerId));

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
                        resultSet.getString("title"),
                        resultSet.getString("tags_json"),
                        resultSet.getString("hostname"),
                        resultSet.getLong("version"),
                        resultSet.getLong("click_total")),
                parameters.toArray());
    }

    @Override
    public List<LinkSuggestion> findSuggestions(int limit, OffsetDateTime now, String query, long ownerId) {
        String pattern = "%" + query.toLowerCase(Locale.ROOT).trim() + "%";

        return jdbcTemplate.query(
                """
                SELECT p.slug, p.title, p.hostname
                FROM link_catalog_projection p
                WHERE p.owner_id = ?
                  AND p.deleted_at IS NULL
                  AND (p.expires_at IS NULL OR p.expires_at > ?)
                  AND (
                    LOWER(p.slug) LIKE ?
                    OR LOWER(p.original_url) LIKE ?
                    OR LOWER(COALESCE(p.title, '')) LIKE ?
                    OR LOWER(COALESCE(p.tags_json, '')) LIKE ?
                    OR LOWER(COALESCE(p.hostname, '')) LIKE ?
                  )
                ORDER BY p.slug ASC
                LIMIT ?
                """,
                (resultSet, rowNum) -> new LinkSuggestion(
                        resultSet.getString("slug"),
                        resultSet.getString("title"),
                        resultSet.getString("hostname")),
                ownerId,
                now,
                pattern,
                pattern,
                pattern,
                pattern,
                pattern,
                limit);
    }

    @Override
    public List<LinkActivityEvent> findRecentActivity(int limit, long ownerId) {
        return jdbcTemplate.query(
                """
                SELECT owner_id, event_type, slug, original_url, title, tags_json, hostname, expires_at, occurred_at
                FROM link_activity_events
                WHERE owner_id = ?
                ORDER BY occurred_at DESC, id DESC
                LIMIT ?
                """,
                (resultSet, rowNum) -> new LinkActivityEvent(
                        resultSet.getLong("owner_id"),
                        LinkActivityType.valueOf(resultSet.getString("event_type")),
                        resultSet.getString("slug"),
                        resultSet.getString("original_url"),
                        resultSet.getString("title"),
                        deserializeTags(resultSet.getString("tags_json")),
                        resultSet.getString("hostname"),
                        resultSet.getObject("expires_at", OffsetDateTime.class),
                        resultSet.getObject("occurred_at", OffsetDateTime.class)),
                ownerId,
                limit);
    }

    @Override
    public Optional<LinkTrafficSummaryTotals> findTrafficSummaryTotals(
            String slug,
            OffsetDateTime last24HoursSince,
            LocalDate last7DaysStartDate,
            long ownerId) {
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
                          AND l.owner_id = ?
                        """,
                        (resultSet, rowNum) -> new LinkTrafficSummaryTotals(
                                resultSet.getString("slug"),
                                resultSet.getString("original_url"),
                                resultSet.getLong("total_clicks"),
                                resultSet.getLong("clicks_last_24_hours"),
                                resultSet.getLong("clicks_last_7_days")),
                        last24HoursSince,
                        last7DaysStartDate,
                        slug,
                        ownerId)
                .stream()
                .findFirst();
    }

    @Override
    public List<DailyClickBucket> findRecentDailyClickBuckets(String slug, LocalDate startDate, long ownerId) {
        return jdbcTemplate.query(
                """
                SELECT r.rollup_date, r.click_count
                FROM link_click_daily_rollups r
                JOIN links l ON l.slug = r.slug
                WHERE r.slug = ?
                  AND l.owner_id = ?
                  AND rollup_date >= ?
                ORDER BY rollup_date ASC
                """,
                (resultSet, rowNum) -> new DailyClickBucket(
                        resultSet.getObject("rollup_date", LocalDate.class),
                        resultSet.getLong("click_count")),
                slug,
                ownerId,
                startDate);
    }

    @Override
    public List<TopLinkTraffic> findTopLinks(LinkTrafficWindow window, OffsetDateTime now, long ownerId) {
        return switch (window) {
            case LAST_24_HOURS -> findTopLinksLast24Hours(now.minusHours(24), ownerId);
            case LAST_7_DAYS -> findTopLinksLast7Days(now.toLocalDate().minusDays(6), ownerId);
        };
    }

    @Override
    public List<TrendingLink> findTrendingLinks(LinkTrafficWindow window, OffsetDateTime now, int limit, long ownerId) {
        return switch (window) {
            case LAST_24_HOURS -> findTrendingLinksLast24Hours(now, limit, ownerId);
            case LAST_7_DAYS -> findTrendingLinksLast7Days(now.toLocalDate(), limit, ownerId);
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
                    LOWER(p.slug) LIKE ?
                    OR LOWER(p.original_url) LIKE ?
                    OR LOWER(COALESCE(p.title, '')) LIKE ?
                    OR LOWER(COALESCE(p.tags_json, '')) LIKE ?
                    OR LOWER(COALESCE(p.hostname, '')) LIKE ?
                  )
                """);
        String pattern = "%" + query.toLowerCase(Locale.ROOT).trim() + "%";
        parameters.add(pattern);
        parameters.add(pattern);
        parameters.add(pattern);
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

    private void upsertDailyRollupCount(String slug, LocalDate rollupDate, long increment) {
        int updated = jdbcTemplate.update(
                """
                UPDATE link_click_daily_rollups
                SET click_count = click_count + ?
                WHERE slug = ? AND rollup_date = ?
                """,
                increment,
                slug,
                rollupDate);
        if (updated == 1) {
            return;
        }

        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO link_click_daily_rollups (slug, rollup_date, click_count)
                    VALUES (?, ?, ?)
                    """,
                    slug,
                    rollupDate,
                    increment);
        } catch (DuplicateKeyException exception) {
            jdbcTemplate.update(
                    """
                    UPDATE link_click_daily_rollups
                    SET click_count = click_count + ?
                    WHERE slug = ? AND rollup_date = ?
                    """,
                    increment,
                    slug,
                    rollupDate);
        }
    }

    private void upsertCatalogProjection(
            LinkLifecycleEvent linkLifecycleEvent,
            String tagsJson,
            OffsetDateTime deletedAt) {
        int updated = jdbcTemplate.update(
                """
                UPDATE link_catalog_projection
                SET original_url = ?,
                    updated_at = ?,
                    title = ?,
                    tags_json = ?,
                    hostname = ?,
                    expires_at = ?,
                    deleted_at = ?,
                    owner_id = ?,
                    version = ?
                WHERE slug = ?
                  AND version < ?
                """,
                linkLifecycleEvent.originalUrl(),
                linkLifecycleEvent.occurredAt(),
                linkLifecycleEvent.title(),
                tagsJson,
                linkLifecycleEvent.hostname(),
                linkLifecycleEvent.expiresAt(),
                deletedAt,
                linkLifecycleEvent.ownerId(),
                linkLifecycleEvent.version(),
                linkLifecycleEvent.slug(),
                linkLifecycleEvent.version());
        if (updated == 1) {
            return;
        }

        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO link_catalog_projection (
                        slug, original_url, created_at, updated_at, title, tags_json, hostname, expires_at, deleted_at, version, owner_id
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    linkLifecycleEvent.slug(),
                    linkLifecycleEvent.originalUrl(),
                    linkLifecycleEvent.occurredAt(),
                    linkLifecycleEvent.occurredAt(),
                    linkLifecycleEvent.title(),
                    tagsJson,
                    linkLifecycleEvent.hostname(),
                    linkLifecycleEvent.expiresAt(),
                    deletedAt,
                    linkLifecycleEvent.version(),
                    linkLifecycleEvent.ownerId());
        } catch (DuplicateKeyException exception) {
            jdbcTemplate.update(
                    """
                    UPDATE link_catalog_projection
                    SET original_url = ?,
                        updated_at = ?,
                        title = ?,
                        tags_json = ?,
                        hostname = ?,
                        expires_at = ?,
                        deleted_at = ?,
                        owner_id = ?,
                        version = ?
                    WHERE slug = ?
                      AND version < ?
                    """,
                    linkLifecycleEvent.originalUrl(),
                    linkLifecycleEvent.occurredAt(),
                    linkLifecycleEvent.title(),
                    tagsJson,
                    linkLifecycleEvent.hostname(),
                    linkLifecycleEvent.expiresAt(),
                    deletedAt,
                    linkLifecycleEvent.ownerId(),
                    linkLifecycleEvent.version(),
                    linkLifecycleEvent.slug(),
                    linkLifecycleEvent.version());
        }
    }

    private List<TopLinkTraffic> findTopLinksLast24Hours(OffsetDateTime since, long ownerId) {
        return jdbcTemplate.query(
                """
                SELECT l.slug, l.original_url, COUNT(*) AS click_total
                FROM links l
                JOIN link_clicks c ON c.slug = l.slug
                WHERE c.clicked_at >= ?
                  AND l.owner_id = ?
                GROUP BY l.slug, l.original_url
                ORDER BY click_total DESC, l.slug ASC
                """,
                (resultSet, rowNum) -> new TopLinkTraffic(
                        resultSet.getString("slug"),
                        resultSet.getString("original_url"),
                        resultSet.getLong("click_total")),
                since,
                ownerId);
    }

    private List<TrendingLink> findTrendingLinksLast24Hours(OffsetDateTime now, int limit, long ownerId) {
        OffsetDateTime currentStart = now.minusHours(24);
        OffsetDateTime previousStart = now.minusHours(48);

        return jdbcTemplate.query(
                """
                SELECT l.slug,
                       l.original_url,
                       COALESCE(current_window.click_total, 0) - COALESCE(previous_window.click_total, 0) AS click_growth,
                       COALESCE(current_window.click_total, 0) AS current_window_clicks,
                       COALESCE(previous_window.click_total, 0) AS previous_window_clicks
                FROM links l
                LEFT JOIN (
                    SELECT slug, COUNT(*) AS click_total
                    FROM link_clicks
                    WHERE clicked_at >= ? AND clicked_at < ?
                    GROUP BY slug
                ) current_window ON current_window.slug = l.slug
                LEFT JOIN (
                    SELECT slug, COUNT(*) AS click_total
                    FROM link_clicks
                    WHERE clicked_at >= ? AND clicked_at < ?
                    GROUP BY slug
                ) previous_window ON previous_window.slug = l.slug
                WHERE COALESCE(current_window.click_total, 0) - COALESCE(previous_window.click_total, 0) > 0
                  AND l.owner_id = ?
                ORDER BY click_growth DESC, current_window_clicks DESC, l.slug ASC
                LIMIT ?
                """,
                (resultSet, rowNum) -> new TrendingLink(
                        resultSet.getString("slug"),
                        resultSet.getString("original_url"),
                        resultSet.getLong("click_growth"),
                        resultSet.getLong("current_window_clicks"),
                        resultSet.getLong("previous_window_clicks")),
                currentStart,
                now,
                previousStart,
                currentStart,
                ownerId,
                limit);
    }

    private List<TopLinkTraffic> findTopLinksLast7Days(LocalDate startDate, long ownerId) {
        return jdbcTemplate.query(
                """
                SELECT l.slug, l.original_url, SUM(r.click_count) AS click_total
                FROM links l
                JOIN link_click_daily_rollups r ON r.slug = l.slug
                WHERE r.rollup_date >= ?
                  AND l.owner_id = ?
                GROUP BY l.slug, l.original_url
                ORDER BY click_total DESC, l.slug ASC
                """,
                (resultSet, rowNum) -> new TopLinkTraffic(
                        resultSet.getString("slug"),
                        resultSet.getString("original_url"),
                        resultSet.getLong("click_total")),
                startDate,
                ownerId);
    }

    private List<TrendingLink> findTrendingLinksLast7Days(LocalDate today, int limit, long ownerId) {
        LocalDate currentStart = today.minusDays(6);
        LocalDate previousStart = currentStart.minusDays(7);

        return jdbcTemplate.query(
                """
                SELECT l.slug,
                       l.original_url,
                       COALESCE(current_window.click_total, 0) - COALESCE(previous_window.click_total, 0) AS click_growth,
                       COALESCE(current_window.click_total, 0) AS current_window_clicks,
                       COALESCE(previous_window.click_total, 0) AS previous_window_clicks
                FROM links l
                LEFT JOIN (
                    SELECT slug, SUM(click_count) AS click_total
                    FROM link_click_daily_rollups
                    WHERE rollup_date >= ?
                    GROUP BY slug
                ) current_window ON current_window.slug = l.slug
                LEFT JOIN (
                    SELECT slug, SUM(click_count) AS click_total
                    FROM link_click_daily_rollups
                    WHERE rollup_date >= ? AND rollup_date < ?
                    GROUP BY slug
                ) previous_window ON previous_window.slug = l.slug
                WHERE COALESCE(current_window.click_total, 0) - COALESCE(previous_window.click_total, 0) > 0
                  AND l.owner_id = ?
                ORDER BY click_growth DESC, current_window_clicks DESC, l.slug ASC
                LIMIT ?
                """,
                (resultSet, rowNum) -> new TrendingLink(
                        resultSet.getString("slug"),
                        resultSet.getString("original_url"),
                        resultSet.getLong("click_growth"),
                        resultSet.getLong("current_window_clicks"),
                        resultSet.getLong("previous_window_clicks")),
                currentStart,
                previousStart,
                currentStart,
                ownerId,
                limit);
    }

    private Link toLink(String slug, String originalUrl) {
        return new Link(new LinkSlug(slug), new OriginalUrl(originalUrl));
    }

    private LinkDetails toLinkDetails(
            String slug,
            String originalUrl,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt,
            String title,
            String tagsJson,
            String hostname,
            long version,
            long clickTotal) {
        return new LinkDetails(slug, originalUrl, createdAt, expiresAt, title, deserializeTags(tagsJson), hostname, version, clickTotal);
    }

    private String serializeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Tags could not be serialized", exception);
        }
    }

    private List<String> deserializeTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return List.of();
        }

        try {
            return objectMapper.readValue(tagsJson, TAG_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Tags could not be deserialized", exception);
        }
    }
}
