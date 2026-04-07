package com.linkplatform.api.link.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkplatform.api.link.domain.Link;
import com.linkplatform.api.link.domain.LinkSlug;
import com.linkplatform.api.link.domain.OriginalUrl;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PostgresLinkStore implements LinkStore {

    private static final TypeReference<List<String>> TAG_LIST_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final JdbcTemplate queryJdbcTemplate;
    private final ObjectMapper objectMapper;

    public PostgresLinkStore(
            JdbcTemplate jdbcTemplate,
            @Qualifier("queryJdbcTemplate") JdbcTemplate queryJdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.queryJdbcTemplate = queryJdbcTemplate;
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
            long workspaceId) {
        try {
            return jdbcTemplate.update(
                """
                    INSERT INTO links (slug, original_url, expires_at, title, tags_json, hostname, version, owner_id, workspace_id, abuse_status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
                    """,
                    link.slug().value(),
                    link.originalUrl().value(),
                    expiresAt,
                    title,
                    serializeTags(tags),
                    hostname,
                    version,
                    resolveLegacyOwnerId(workspaceId),
                    workspaceId) == 1;
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
            long workspaceId) {
        return jdbcTemplate.update(
                """
                UPDATE links
                SET original_url = ?, expires_at = ?, title = ?, tags_json = ?, hostname = ?, version = ?
                WHERE slug = ?
                  AND version = ?
                  AND workspace_id = ?
                """,
                originalUrl,
                expiresAt,
                title,
                serializeTags(tags),
                hostname,
                nextVersion,
                slug,
                expectedVersion,
                workspaceId) == 1;
    }

    @Override
    public boolean deleteBySlug(String slug, long expectedVersion, long workspaceId) {
        return jdbcTemplate.update(
                        "DELETE FROM links WHERE slug = ? AND version = ? AND workspace_id = ?",
                        slug,
                        expectedVersion,
                        workspaceId)
                == 1;
    }

    @Override
    public boolean updateLifecycle(
            String slug,
            LinkLifecycleState expectedState,
            LinkLifecycleState nextState,
            OffsetDateTime expiresAt,
            long expectedVersion,
            long nextVersion,
            long workspaceId) {
        return jdbcTemplate.update(
                """
                UPDATE links
                SET lifecycle_state = ?, expires_at = ?, version = ?
                WHERE slug = ?
                  AND lifecycle_state = ?
                  AND version = ?
                  AND workspace_id = ?
                """,
                nextState.name(),
                expiresAt,
                nextVersion,
                slug,
                expectedState.name(),
                expectedVersion,
                workspaceId) == 1;
    }

    @Override
    public boolean restoreDeleted(
            DeletedLinkSnapshot deletedLinkSnapshot,
            LinkLifecycleState restoredState,
            long nextVersion,
            long workspaceId) {
        try {
            return jdbcTemplate.update(
                    """
                    INSERT INTO links (
                        slug, original_url, created_at, expires_at, title, tags_json, hostname, version, owner_id, workspace_id, lifecycle_state
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    deletedLinkSnapshot.slug(),
                    deletedLinkSnapshot.originalUrl(),
                    deletedLinkSnapshot.createdAt(),
                    deletedLinkSnapshot.expiresAt(),
                    deletedLinkSnapshot.title(),
                    serializeTags(deletedLinkSnapshot.tags()),
                    deletedLinkSnapshot.hostname(),
                    nextVersion,
                    resolveLegacyOwnerId(workspaceId),
                    workspaceId,
                    restoredState.name()) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public long countActiveLinksByOwner(long workspaceId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM links
                WHERE workspace_id = ?
                  AND lifecycle_state = 'ACTIVE'
                  AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
                """,
                Long.class,
                workspaceId);
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
                        event_id, owner_id, workspace_id, event_type, slug, original_url, title, tags_json, hostname, expires_at, occurred_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    eventId,
                    linkActivityEvent.ownerId(),
                    linkActivityEvent.workspaceId(),
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
    public Optional<LinkLifecycleState> findLifecycleStateBySlug(String slug, long workspaceId) {
        return jdbcTemplate.query(
                        "SELECT lifecycle_state FROM links WHERE slug = ? AND workspace_id = ?",
                        (resultSet, rowNum) -> LinkLifecycleState.valueOf(resultSet.getString("lifecycle_state")),
                        slug,
                        workspaceId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<LinkAbuseStatus> findAbuseStatusBySlug(String slug, long workspaceId) {
        return jdbcTemplate.query(
                        "SELECT abuse_status FROM links WHERE slug = ? AND workspace_id = ?",
                        (resultSet, rowNum) -> LinkAbuseStatus.valueOf(resultSet.getString("abuse_status")),
                        slug,
                        workspaceId)
                .stream()
                .findFirst();
    }

    @Override
    public boolean flagLinkForAbuse(
            long workspaceId,
            String slug,
            String abuseReason,
            OffsetDateTime flaggedAt,
            Long reviewedByOwnerId,
            String reviewNote,
            boolean preserveQuarantinedState) {
        String quarantineGuard = preserveQuarantinedState ? " AND abuse_status <> 'QUARANTINED'" : "";
        return jdbcTemplate.update(
                """
                UPDATE links
                SET abuse_status = 'FLAGGED',
                    abuse_reason = ?,
                    abuse_flagged_at = ?,
                    abuse_reviewed_at = ?,
                    abuse_reviewed_by_owner_id = ?,
                    abuse_review_note = ?
                WHERE workspace_id = ?
                  AND slug = ?
                """
                        + quarantineGuard,
                abuseReason,
                flaggedAt,
                null,
                reviewedByOwnerId,
                reviewNote,
                workspaceId,
                slug) >= 1;
    }

    @Override
    public boolean quarantineLink(
            long workspaceId,
            String slug,
            String abuseReason,
            OffsetDateTime reviewedAt,
            Long reviewedByOwnerId,
            String reviewNote) {
        return jdbcTemplate.update(
                """
                UPDATE links
                SET abuse_status = 'QUARANTINED',
                    abuse_reason = ?,
                    abuse_flagged_at = ?,
                    abuse_reviewed_at = ?,
                    abuse_reviewed_by_owner_id = ?,
                    abuse_review_note = ?
                WHERE workspace_id = ?
                  AND slug = ?
                """,
                abuseReason,
                reviewedAt,
                reviewedAt,
                reviewedByOwnerId,
                reviewNote,
                workspaceId,
                slug) == 1;
    }

    @Override
    public boolean releaseLink(
            long workspaceId,
            String slug,
            Long reviewedByOwnerId,
            String reviewNote,
            OffsetDateTime reviewedAt) {
        return jdbcTemplate.update(
                """
                UPDATE links
                SET abuse_status = 'ACTIVE',
                    abuse_reason = NULL,
                    abuse_reviewed_at = ?,
                    abuse_reviewed_by_owner_id = ?,
                    abuse_review_note = ?
                WHERE workspace_id = ?
                  AND slug = ?
                """,
                reviewedAt,
                reviewedByOwnerId,
                reviewNote,
                workspaceId,
                slug) == 1;
    }

    @Override
    public boolean clearFlaggedLink(
            long workspaceId,
            String slug,
            Long reviewedByOwnerId,
            String reviewNote,
            OffsetDateTime reviewedAt) {
        return jdbcTemplate.update(
                """
                UPDATE links
                SET abuse_status = 'ACTIVE',
                    abuse_reason = NULL,
                    abuse_reviewed_at = ?,
                    abuse_reviewed_by_owner_id = ?,
                    abuse_review_note = ?
                WHERE workspace_id = ?
                  AND slug = ?
                  AND abuse_status = 'FLAGGED'
                """,
                reviewedAt,
                reviewedByOwnerId,
                reviewNote,
                workspaceId,
                slug) == 1;
    }

    @Override
    public List<Long> findOwnerIdsWithClickHistory() {
        return findOwnerIdsWithClickHistory(null, null);
    }

    @Override
    public List<Long> findOwnerIdsWithClickHistory(Long ownerId, String slug) {
        return findOwnerIdsWithClickHistory(null, ownerId, slug, null, null);
    }

    @Override
    public List<Long> findOwnerIdsWithClickHistory(Long workspaceId, Long ownerId, String slug, OffsetDateTime from, OffsetDateTime to) {
        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT l.owner_id
                FROM links l
                JOIN link_clicks c ON c.slug = l.slug
                WHERE 1 = 1
                """);
        List<Object> parameters = new ArrayList<>();
        if (ownerId != null) {
            sql.append(" AND l.owner_id = ?");
            parameters.add(ownerId);
        }
        if (slug != null && !slug.isBlank()) {
            sql.append(" AND l.slug = ?");
            parameters.add(slug);
        }
        if (workspaceId != null) {
            sql.append(" AND l.workspace_id = ?");
            parameters.add(workspaceId);
        }
        if (from != null) {
            sql.append(" AND c.clicked_at >= ?");
            parameters.add(from);
        }
        if (to != null) {
            sql.append(" AND c.clicked_at < ?");
            parameters.add(to);
        }
        sql.append(" ORDER BY l.owner_id ASC");
        return jdbcTemplate.query(
                sql.toString(),
                (resultSet, rowNum) -> resultSet.getLong("owner_id"),
                parameters.toArray());
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
            case CREATED, UPDATED, RESTORED, EXPIRED, EXPIRATION_UPDATED, SUSPENDED, RESUMED, ARCHIVED, UNARCHIVED -> upsertCatalogProjection(
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
    public void projectDiscoveryEvent(LinkLifecycleEvent linkLifecycleEvent) {
        String tagsJson = serializeTags(linkLifecycleEvent.tags());
        String lifecycleState = determineLifecycleState(linkLifecycleEvent, OffsetDateTime.now());
        switch (linkLifecycleEvent.eventType()) {
            case CREATED, UPDATED, RESTORED, EXPIRED, EXPIRATION_UPDATED, SUSPENDED, RESUMED, ARCHIVED, UNARCHIVED -> upsertDiscoveryProjection(
                    linkLifecycleEvent,
                    tagsJson,
                    null,
                    lifecycleState);
            case DELETED -> upsertDiscoveryProjection(
                    linkLifecycleEvent,
                    tagsJson,
                    linkLifecycleEvent.occurredAt(),
                    LinkDiscoveryLifecycleState.DELETED.name());
        }
    }

    @Override
    public void resetCatalogProjection() {
        resetCatalogProjection(null, null);
    }

    @Override
    public void resetCatalogProjection(Long ownerId, String slug) {
        deleteProjectionRows("link_catalog_projection", ownerId, slug);
    }

    @Override
    public void resetCatalogProjection(Long workspaceId, Long ownerId, String slug) {
        deleteProjectionRows("link_catalog_projection", workspaceId, ownerId, slug);
    }

    @Override
    public void resetDiscoveryProjection() {
        resetDiscoveryProjection(null, null);
    }

    @Override
    public void resetDiscoveryProjection(Long ownerId, String slug) {
        deleteProjectionRows("link_discovery_projection", ownerId, slug);
    }

    @Override
    public void resetDiscoveryProjection(Long workspaceId, Long ownerId, String slug) {
        deleteProjectionRows("link_discovery_projection", workspaceId, ownerId, slug);
    }

    @Override
    public List<LinkClickHistoryRecord> findClickHistoryChunkAfter(long afterId, int limit) {
        return findClickHistoryChunkAfter(afterId, limit, null, null);
    }

    @Override
    public List<LinkClickHistoryRecord> findClickHistoryChunkAfter(long afterId, int limit, Long ownerId, String slug) {
        return findClickHistoryChunkAfter(afterId, limit, null, ownerId, slug, null, null);
    }

    @Override
    public List<LinkClickHistoryRecord> findClickHistoryChunkAfter(
            long afterId,
            int limit,
            Long workspaceId,
            Long ownerId,
            String slug,
            OffsetDateTime from,
            OffsetDateTime to) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.id, c.slug, CAST(c.clicked_at AS DATE) AS rollup_date
                FROM link_clicks c
                JOIN links l ON l.slug = c.slug
                WHERE c.id > ?
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(afterId);
        if (ownerId != null) {
            sql.append(" AND l.owner_id = ?");
            parameters.add(ownerId);
        }
        if (slug != null && !slug.isBlank()) {
            sql.append(" AND c.slug = ?");
            parameters.add(slug);
        }
        if (workspaceId != null) {
            sql.append(" AND l.workspace_id = ?");
            parameters.add(workspaceId);
        }
        if (from != null) {
            sql.append(" AND c.clicked_at >= ?");
            parameters.add(from);
        }
        if (to != null) {
            sql.append(" AND c.clicked_at < ?");
            parameters.add(to);
        }
        sql.append("\nORDER BY c.id ASC\nLIMIT ?\n");
        parameters.add(limit);
        return jdbcTemplate.query(
                sql.toString(),
                (resultSet, rowNum) -> new LinkClickHistoryRecord(
                        resultSet.getLong("id"),
                        resultSet.getString("slug"),
                        resultSet.getObject("rollup_date", LocalDate.class)),
                parameters.toArray());
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
        resetClickDailyRollups(null, null);
    }

    @Override
    public void resetClickDailyRollups(Long ownerId, String slug) {
        resetClickDailyRollups(null, ownerId, slug, null, null);
    }

    @Override
    public void resetClickDailyRollups(Long workspaceId, Long ownerId, String slug, OffsetDateTime from, OffsetDateTime to) {
        if (workspaceId == null && ownerId == null && (slug == null || slug.isBlank()) && from == null && to == null) {
            jdbcTemplate.update("DELETE FROM link_click_daily_rollups");
            return;
        }
        StringBuilder sql = new StringBuilder("""
                DELETE FROM link_click_daily_rollups
                WHERE slug IN (
                    SELECT l.slug
                    FROM links l
                    WHERE 1 = 1
                """);
        List<Object> parameters = new ArrayList<>();
        if (ownerId != null) {
            sql.append(" AND l.owner_id = ?");
            parameters.add(ownerId);
        }
        if (slug != null && !slug.isBlank()) {
            sql.append(" AND l.slug = ?");
            parameters.add(slug);
        }
        if (workspaceId != null) {
            sql.append(" AND l.workspace_id = ?");
            parameters.add(workspaceId);
        }
        if (from != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM link_clicks c WHERE c.slug = l.slug AND c.clicked_at >= ?");
            parameters.add(from);
            if (to != null) {
                sql.append(" AND c.clicked_at < ?");
                parameters.add(to);
            }
            sql.append(")");
        }
        sql.append(")");
        jdbcTemplate.update(sql.toString(), parameters.toArray());
    }

    @Override
    public List<LinkClickHistoryRecord> findClickHistoryChunkForReconciliationAfter(long afterId, int limit) {
        return findClickHistoryChunkForReconciliationAfter(afterId, limit, null, null);
    }

    @Override
    public List<LinkClickHistoryRecord> findClickHistoryChunkForReconciliationAfter(long afterId, int limit, Long ownerId, String slug) {
        return findClickHistoryChunkForReconciliationAfter(afterId, limit, null, ownerId, slug, null, null);
    }

    @Override
    public List<LinkClickHistoryRecord> findClickHistoryChunkForReconciliationAfter(
            long afterId,
            int limit,
            Long workspaceId,
            Long ownerId,
            String slug,
            OffsetDateTime from,
            OffsetDateTime to) {
        return findClickHistoryChunkAfter(afterId, limit, workspaceId, ownerId, slug, from, to);
    }

    @Override
    public java.util.Map<String, Long> findDailyRollupTotalsBySlugAndDay(java.util.Set<String> slugDayKeys) {
        java.util.Map<String, Long> results = new java.util.HashMap<>();
        for (String slugDayKey : slugDayKeys) {
            int delimiterIndex = slugDayKey.indexOf('|');
            String slug = slugDayKey.substring(0, delimiterIndex);
            LocalDate bucketDay = LocalDate.parse(slugDayKey.substring(delimiterIndex + 1));
            Long count = jdbcTemplate.queryForObject(
                    """
                    SELECT click_count
                    FROM link_click_daily_rollups
                    WHERE slug = ? AND rollup_date = ?
                    """,
                    Long.class,
                    slug,
                    bucketDay);
            results.put(slugDayKey, count == null ? 0L : count);
        }
        return results;
    }

    @Override
    public void upsertClickRollupReconciliation(ClickRollupDriftRecord driftRecord) {
        jdbcTemplate.update(
                """
                INSERT INTO click_rollup_reconciliation (
                    owner_id, slug, bucket_day, raw_click_count, rollup_click_count, drift_count,
                    detected_at, repaired_at, repair_status, repair_note
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (owner_id, slug, bucket_day) DO UPDATE
                SET raw_click_count = EXCLUDED.raw_click_count,
                    rollup_click_count = EXCLUDED.rollup_click_count,
                    drift_count = EXCLUDED.drift_count,
                    detected_at = EXCLUDED.detected_at,
                    repaired_at = EXCLUDED.repaired_at,
                    repair_status = EXCLUDED.repair_status,
                    repair_note = EXCLUDED.repair_note
                """,
                driftRecord.ownerId(),
                driftRecord.slug(),
                driftRecord.bucketDay(),
                driftRecord.rawClickCount(),
                driftRecord.rollupClickCount(),
                driftRecord.driftCount(),
                driftRecord.detectedAt(),
                driftRecord.repairedAt(),
                driftRecord.repairStatus().name(),
                driftRecord.repairNote());
    }

    @Override
    public void repairDailyRollupTotal(String slug, LocalDate bucketDay, long rawClickCount) {
        jdbcTemplate.update(
                """
                INSERT INTO link_click_daily_rollups (slug, rollup_date, click_count)
                VALUES (?, ?, ?)
                ON CONFLICT (slug, rollup_date) DO UPDATE
                SET click_count = EXCLUDED.click_count
                """,
                slug,
                bucketDay,
                rawClickCount);
    }

    @Override
    public Optional<Link> findBySlug(String slug, OffsetDateTime now) {
        return jdbcTemplate.query(
                """
                        SELECT slug, original_url
                        FROM links
                        WHERE slug = ?
                          AND lifecycle_state = 'ACTIVE'
                          AND abuse_status <> 'QUARANTINED'
                          AND (expires_at IS NULL OR expires_at > ?)
                        """,
                        (resultSet, rowNum) -> toLink(resultSet.getString("slug"), resultSet.getString("original_url")),
                        slug,
                        now)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<LinkDetails> findDetailsBySlug(String slug, OffsetDateTime now, long workspaceId) {
        return queryJdbcTemplate.query(
                        """
                        SELECT l.slug,
                               l.original_url,
                               l.created_at,
                               l.expires_at,
                               l.title,
                               l.tags_json,
                               l.hostname,
                               l.abuse_status,
                               l.version,
                               COALESCE((SELECT SUM(r.click_count) FROM link_click_daily_rollups r WHERE r.slug = l.slug), 0) AS click_total
                        FROM links l
                        WHERE l.slug = ?
                          AND l.workspace_id = ?
                          AND l.lifecycle_state <> 'ARCHIVED'
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
                                LinkAbuseStatus.valueOf(resultSet.getString("abuse_status")),
                                resultSet.getLong("version"),
                                resultSet.getLong("click_total")),
                        slug,
                        workspaceId,
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
                               l.abuse_status,
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
                                LinkAbuseStatus.valueOf(resultSet.getString("abuse_status")),
                                resultSet.getLong("version"),
                                resultSet.getLong("click_total")),
                        slug)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<LinkDetails> findStoredDetailsBySlug(String slug, long workspaceId) {
        return jdbcTemplate.query(
                        """
                        SELECT l.slug,
                               l.original_url,
                               l.created_at,
                               l.expires_at,
                               l.title,
                               l.tags_json,
                               l.hostname,
                               l.abuse_status,
                               l.version,
                               COALESCE((SELECT SUM(r.click_count) FROM link_click_daily_rollups r WHERE r.slug = l.slug), 0) AS click_total
                        FROM links l
                        WHERE l.slug = ?
                          AND l.workspace_id = ?
                        """,
                        (resultSet, rowNum) -> toLinkDetails(
                                resultSet.getString("slug"),
                                resultSet.getString("original_url"),
                                resultSet.getObject("created_at", OffsetDateTime.class),
                                resultSet.getObject("expires_at", OffsetDateTime.class),
                                resultSet.getString("title"),
                                resultSet.getString("tags_json"),
                                resultSet.getString("hostname"),
                                LinkAbuseStatus.valueOf(resultSet.getString("abuse_status")),
                                resultSet.getLong("version"),
                                resultSet.getLong("click_total")),
                        slug,
                        workspaceId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<DeletedLinkSnapshot> findDeletedSnapshotBySlug(String slug, long workspaceId) {
        return jdbcTemplate.query(
                        """
                        SELECT slug,
                               original_url,
                               created_at,
                               expires_at,
                               title,
                               tags_json,
                               hostname,
                               version,
                               lifecycle_state
                        FROM link_catalog_projection
                        WHERE slug = ?
                          AND workspace_id = ?
                          AND deleted_at IS NOT NULL
                        """,
                        (resultSet, rowNum) -> new DeletedLinkSnapshot(
                                resultSet.getString("slug"),
                                resultSet.getString("original_url"),
                                resultSet.getObject("created_at", OffsetDateTime.class),
                                resultSet.getObject("expires_at", OffsetDateTime.class),
                                resultSet.getString("title"),
                                deserializeTags(resultSet.getString("tags_json")),
                                resultSet.getString("hostname"),
                                resultSet.getLong("version"),
                                LinkLifecycleState.valueOf(resultSet.getString("lifecycle_state"))),
                        slug,
                        workspaceId)
                .stream()
                .findFirst();
    }

    @Override
    public List<LinkDetails> findRecent(
            int limit,
            OffsetDateTime now,
            String query,
            LinkLifecycleState state,
            LinkAbuseStatus abuseStatus,
            long workspaceId) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.slug,
                       p.original_url,
                       p.created_at,
                       p.expires_at,
                       p.title,
                       p.tags_json,
                       p.hostname,
                       l.abuse_status,
                       p.version,
                       COALESCE((SELECT SUM(r.click_count) FROM link_click_daily_rollups r WHERE r.slug = p.slug), 0) AS click_total
                FROM link_catalog_projection p
                JOIN links l ON l.slug = p.slug AND l.workspace_id = p.workspace_id
                WHERE 1 = 1
                  AND p.workspace_id = ?
                  AND p.deleted_at IS NULL
                """);
        List<Object> parameters = new ArrayList<>(List.of(workspaceId));

        appendLifecycleClause(sql, parameters, now, state);
        appendAbuseClause(sql, parameters, abuseStatus, "l");
        appendSearchClause(sql, parameters, query);

        sql.append("""
                
                ORDER BY p.updated_at DESC, p.created_at DESC, p.slug ASC
                LIMIT ?
                """);
        parameters.add(limit);

        return queryJdbcTemplate.query(
                sql.toString(),
                (resultSet, rowNum) -> toLinkDetails(
                        resultSet.getString("slug"),
                        resultSet.getString("original_url"),
                        resultSet.getObject("created_at", OffsetDateTime.class),
                        resultSet.getObject("expires_at", OffsetDateTime.class),
                        resultSet.getString("title"),
                        resultSet.getString("tags_json"),
                        resultSet.getString("hostname"),
                        LinkAbuseStatus.valueOf(resultSet.getString("abuse_status")),
                        resultSet.getLong("version"),
                        resultSet.getLong("click_total")),
                parameters.toArray());
    }

    @Override
    public List<LinkSuggestion> findSuggestions(int limit, OffsetDateTime now, String query, long workspaceId) {
        String pattern = "%" + query.toLowerCase(Locale.ROOT).trim() + "%";

        return queryJdbcTemplate.query(
                """
                SELECT p.slug, p.title, p.hostname
                FROM link_catalog_projection p
                WHERE p.workspace_id = ?
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
                workspaceId,
                now,
                pattern,
                pattern,
                pattern,
                pattern,
                pattern,
                limit);
    }

    @Override
    public LinkDiscoveryPage searchDiscovery(OffsetDateTime now, long workspaceId, LinkDiscoveryQuery query) {
        StringBuilder sql = new StringBuilder("""
                SELECT d.slug,
                       d.original_url,
                       d.title,
                       d.hostname,
                       d.tags_json,
                       l.abuse_status,
                       d.created_at,
                       d.updated_at,
                       d.expires_at,
                       d.deleted_at,
                       d.version
                FROM link_discovery_projection d
                JOIN links l ON l.slug = d.slug AND l.workspace_id = d.workspace_id
                WHERE d.workspace_id = ?
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(workspaceId);

        appendDiscoverySearchClause(sql, parameters, query.searchText());
        appendDiscoveryHostnameClause(sql, parameters, query.hostname());
        appendDiscoveryTagClause(sql, parameters, query.tag());
        appendAbuseClause(sql, parameters, query.abuseStatus(), "l");
        appendDiscoveryLifecycleClause(sql, parameters, now, query.lifecycle());
        appendDiscoveryExpirationClause(sql, parameters, now, query.expiration());
        appendDiscoveryCursorClause(sql, parameters, query.sort(), query.cursor());
        sql.append(" ORDER BY ").append(discoveryOrderBy(query.sort())).append(" LIMIT ?");
        parameters.add(query.limit() + 1);

        List<LinkDiscoveryItem> fetchedItems = queryJdbcTemplate.query(
                sql.toString(),
                (resultSet, rowNum) -> new LinkDiscoveryItem(
                        resultSet.getString("slug"),
                        resultSet.getString("original_url"),
                        resultSet.getString("title"),
                        resultSet.getString("hostname"),
                        deserializeTags(resultSet.getString("tags_json")),
                        LinkAbuseStatus.valueOf(resultSet.getString("abuse_status")),
                        mapLifecycleState(
                                resultSet.getObject("deleted_at", OffsetDateTime.class),
                                resultSet.getObject("expires_at", OffsetDateTime.class),
                                now),
                        resultSet.getObject("created_at", OffsetDateTime.class),
                        resultSet.getObject("updated_at", OffsetDateTime.class),
                        resultSet.getObject("expires_at", OffsetDateTime.class),
                        resultSet.getObject("deleted_at", OffsetDateTime.class),
                        resultSet.getLong("version")),
                parameters.toArray());

        boolean hasMore = fetchedItems.size() > query.limit();
        List<LinkDiscoveryItem> pageItems = hasMore ? fetchedItems.subList(0, query.limit()) : fetchedItems;
        String nextCursor = hasMore && !pageItems.isEmpty() ? encodeCursor(query.sort(), pageItems.getLast()) : null;
        return new LinkDiscoveryPage(List.copyOf(pageItems), nextCursor, hasMore);
    }

    @Override
    public List<LinkActivityEvent> findRecentActivity(int limit, long workspaceId) {
        return queryJdbcTemplate.query(
                """
                SELECT owner_id, workspace_id, event_type, slug, original_url, title, tags_json, hostname, expires_at, occurred_at
                FROM link_activity_events
                WHERE workspace_id = ?
                ORDER BY occurred_at DESC, id DESC
                LIMIT ?
                """,
                (resultSet, rowNum) -> new LinkActivityEvent(
                        resultSet.getLong("owner_id"),
                        resultSet.getLong("workspace_id"),
                        LinkActivityType.valueOf(resultSet.getString("event_type")),
                        resultSet.getString("slug"),
                        resultSet.getString("original_url"),
                        resultSet.getString("title"),
                        deserializeTags(resultSet.getString("tags_json")),
                        resultSet.getString("hostname"),
                        resultSet.getObject("expires_at", OffsetDateTime.class),
                        resultSet.getObject("occurred_at", OffsetDateTime.class)),
                workspaceId,
                limit);
    }

    @Override
    public List<LinkActivityEvent> findRecentActivity(
            int limit,
            String tag,
            LinkLifecycleState lifecycle,
            OffsetDateTime asOf,
            long workspaceId) {
        StringBuilder sql = new StringBuilder("""
                SELECT a.owner_id, a.workspace_id, a.event_type, a.slug, a.original_url, a.title, a.tags_json, a.hostname, a.expires_at, a.occurred_at
                FROM link_activity_events a
                JOIN link_catalog_projection p ON p.slug = a.slug AND p.workspace_id = a.workspace_id
                WHERE a.workspace_id = ?
                """);
        List<Object> parameters = new ArrayList<>(List.of(workspaceId));
        appendCatalogTagClause(sql, parameters, tag);
        appendCatalogLifecycleClause(sql, parameters, asOf, lifecycle);
        sql.append("""
                
                ORDER BY a.occurred_at DESC, a.id DESC
                LIMIT ?
                """);
        parameters.add(limit);
        return queryJdbcTemplate.query(
                sql.toString(),
                (resultSet, rowNum) -> new LinkActivityEvent(
                        resultSet.getLong("owner_id"),
                        resultSet.getLong("workspace_id"),
                        LinkActivityType.valueOf(resultSet.getString("event_type")),
                        resultSet.getString("slug"),
                        resultSet.getString("original_url"),
                        resultSet.getString("title"),
                        deserializeTags(resultSet.getString("tags_json")),
                        resultSet.getString("hostname"),
                        resultSet.getObject("expires_at", OffsetDateTime.class),
                        resultSet.getObject("occurred_at", OffsetDateTime.class)),
                parameters.toArray());
    }

    @Override
    public Optional<LinkTrafficSummaryTotals> findTrafficSummaryTotals(
            String slug,
            OffsetDateTime last24HoursSince,
            LocalDate last7DaysStartDate,
            long workspaceId) {
        return queryJdbcTemplate.query(
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
                          AND l.workspace_id = ?
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
                        workspaceId)
                .stream()
                .findFirst();
    }

    @Override
    public List<DailyClickBucket> findRecentDailyClickBuckets(String slug, LocalDate startDate, long workspaceId) {
        return queryJdbcTemplate.query(
                """
                SELECT r.rollup_date, r.click_count
                FROM link_click_daily_rollups r
                JOIN links l ON l.slug = r.slug
                WHERE r.slug = ?
                  AND l.workspace_id = ?
                  AND rollup_date >= ?
                ORDER BY rollup_date ASC
                """,
                (resultSet, rowNum) -> new DailyClickBucket(
                        resultSet.getObject("rollup_date", LocalDate.class),
                        resultSet.getLong("click_count")),
                slug,
                workspaceId,
                startDate);
    }

    @Override
    public List<TopLinkTraffic> findTopLinks(LinkTrafficWindow window, OffsetDateTime now, long ownerId) {
        return switch (window) {
            case LAST_24_HOURS -> findTopLinksLast24Hours(now.minusHours(24), ownerId, Integer.MAX_VALUE, null, null, now);
            case LAST_7_DAYS -> findTopLinksLast7Days(now.toLocalDate().minusDays(6), ownerId, Integer.MAX_VALUE, null, null, now);
        };
    }

    @Override
    public List<TrendingLink> findTrendingLinks(LinkTrafficWindow window, OffsetDateTime now, int limit, long ownerId) {
        return switch (window) {
            case LAST_24_HOURS -> findTrendingLinksLast24Hours(now, limit, ownerId, null, null, now);
            case LAST_7_DAYS -> findTrendingLinksLast7Days(now.toLocalDate(), limit, ownerId, null, null, now);
        };
    }

    @Override
    public List<TopLinkTraffic> findTopLinks(
            LinkTrafficWindow window,
            OffsetDateTime now,
            int limit,
            String tag,
            LinkLifecycleState lifecycle,
            long ownerId) {
        return switch (window) {
            case LAST_24_HOURS -> findTopLinksLast24Hours(now.minusHours(24), ownerId, limit, tag, lifecycle, now);
            case LAST_7_DAYS -> findTopLinksLast7Days(now.toLocalDate().minusDays(6), ownerId, limit, tag, lifecycle, now);
        };
    }

    @Override
    public List<TopLinkTraffic> findTopLinks(
            OffsetDateTime from,
            OffsetDateTime to,
            int limit,
            String tag,
            LinkLifecycleState lifecycle,
            OffsetDateTime asOf,
            long ownerId) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.slug, p.original_url, COUNT(*) AS click_total
                FROM link_catalog_projection p
                JOIN link_clicks c ON c.slug = p.slug
                WHERE p.owner_id = ?
                  AND c.clicked_at >= ?
                  AND c.clicked_at < ?
                """);
        List<Object> parameters = new ArrayList<>(List.of(ownerId, from, to));
        appendCatalogTagClause(sql, parameters, tag);
        appendCatalogLifecycleClause(sql, parameters, asOf, lifecycle);
        sql.append("""
                
                GROUP BY p.slug, p.original_url
                ORDER BY click_total DESC, p.slug ASC
                LIMIT ?
                """);
        parameters.add(limit);
        return queryJdbcTemplate.query(
                sql.toString(),
                (resultSet, rowNum) -> new TopLinkTraffic(
                        resultSet.getString("slug"),
                        resultSet.getString("original_url"),
                        resultSet.getLong("click_total")),
                parameters.toArray());
    }

    @Override
    public List<TrendingLink> findTrendingLinks(
            LinkTrafficWindow window,
            OffsetDateTime now,
            int limit,
            String tag,
            LinkLifecycleState lifecycle,
            long ownerId) {
        return switch (window) {
            case LAST_24_HOURS -> findTrendingLinksLast24Hours(now, limit, ownerId, tag, lifecycle, now);
            case LAST_7_DAYS -> findTrendingLinksLast7Days(now.toLocalDate(), limit, ownerId, tag, lifecycle, now);
        };
    }

    @Override
    public List<TrendingLink> findTrendingLinks(
            OffsetDateTime from,
            OffsetDateTime to,
            int limit,
            String tag,
            LinkLifecycleState lifecycle,
            OffsetDateTime asOf,
            long ownerId) {
        OffsetDateTime previousStart = from.minus(java.time.Duration.between(from, to));
        return queryRangeTrendingLinks(
                """
                SELECT p.slug,
                       p.original_url,
                       COALESCE(current_window.click_total, 0) - COALESCE(previous_window.click_total, 0) AS click_growth,
                       COALESCE(current_window.click_total, 0) AS current_window_clicks,
                       COALESCE(previous_window.click_total, 0) AS previous_window_clicks
                FROM link_catalog_projection p
                LEFT JOIN (
                    SELECT c.slug, COUNT(*) AS click_total
                    FROM link_clicks c
                    JOIN link_catalog_projection p_current ON p_current.slug = c.slug
                    WHERE p_current.owner_id = ?
                      AND c.clicked_at >= ?
                      AND c.clicked_at < ?
                    GROUP BY c.slug
                ) current_window ON current_window.slug = p.slug
                LEFT JOIN (
                    SELECT c.slug, COUNT(*) AS click_total
                    FROM link_clicks c
                    JOIN link_catalog_projection p_previous ON p_previous.slug = c.slug
                    WHERE p_previous.owner_id = ?
                      AND c.clicked_at >= ?
                      AND c.clicked_at < ?
                    GROUP BY c.slug
                ) previous_window ON previous_window.slug = p.slug
                WHERE p.owner_id = ?
                """,
                List.of(ownerId, from, to, ownerId, previousStart, from, ownerId),
                limit,
                tag,
                lifecycle,
                asOf);
    }

    @Override
    public long countClicksForSlugInRange(String slug, OffsetDateTime from, OffsetDateTime to, long workspaceId) {
        Long clickCount = queryJdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM link_clicks c
                JOIN links l ON l.slug = c.slug
                WHERE c.slug = ?
                  AND l.workspace_id = ?
                  AND c.clicked_at >= ?
                  AND c.clicked_at < ?
                """,
                Long.class,
                slug,
                workspaceId,
                from,
                to);
        return clickCount == null ? 0L : clickCount;
    }

    @Override
    public List<LinkTrafficSeriesBucket> findTrafficSeries(
            String slug,
            OffsetDateTime from,
            OffsetDateTime to,
            String granularity,
            long workspaceId) {
        String bucketExpression = switch (granularity) {
            case "hour" -> "DATE_TRUNC('hour', c.clicked_at)";
            case "day" -> "DATE_TRUNC('day', c.clicked_at)";
            default -> throw new IllegalArgumentException("granularity must be one of: hour, day");
        };
        String sql = """
                SELECT %s AS bucket_start, COUNT(*) AS click_total
                FROM link_clicks c
                JOIN links l ON l.slug = c.slug
                WHERE c.slug = ?
                  AND l.workspace_id = ?
                  AND c.clicked_at >= ?
                  AND c.clicked_at < ?
                GROUP BY %s
                ORDER BY bucket_start ASC
                """.formatted(bucketExpression, bucketExpression);
        return queryJdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> {
                    OffsetDateTime bucketStart = resultSet.getObject("bucket_start", OffsetDateTime.class);
                    return new LinkTrafficSeriesBucket(
                            bucketStart,
                            "hour".equals(granularity) ? bucketStart.plusHours(1) : bucketStart.plusDays(1),
                            resultSet.getLong("click_total"));
                },
                slug,
                workspaceId,
                from,
                to);
    }

    @Override
    public Optional<OffsetDateTime> findLatestMaterializedClickAt(long workspaceId) {
        return Optional.ofNullable(queryJdbcTemplate.queryForObject(
                """
                SELECT MAX(c.clicked_at)
                FROM link_clicks c
                JOIN links l ON l.slug = c.slug
                WHERE l.workspace_id = ?
                """,
                OffsetDateTime.class,
                workspaceId));
    }

    @Override
    public Optional<OffsetDateTime> findLatestMaterializedClickAt(String slug, long workspaceId) {
        return Optional.ofNullable(queryJdbcTemplate.queryForObject(
                """
                SELECT MAX(c.clicked_at)
                FROM link_clicks c
                JOIN links l ON l.slug = c.slug
                WHERE l.workspace_id = ?
                  AND c.slug = ?
                """,
                OffsetDateTime.class,
                workspaceId,
                slug));
    }

    @Override
    public Optional<OffsetDateTime> findLatestMaterializedActivityAt(long workspaceId) {
        return Optional.ofNullable(queryJdbcTemplate.queryForObject(
                """
                SELECT MAX(occurred_at)
                FROM link_activity_events
                WHERE workspace_id = ?
                """,
                OffsetDateTime.class,
                workspaceId));
    }

    @Override
    public Optional<OffsetDateTime> findLatestMaterializedActivityAt(String slug, long workspaceId) {
        return Optional.ofNullable(queryJdbcTemplate.queryForObject(
                """
                SELECT MAX(occurred_at)
                FROM link_activity_events
                WHERE workspace_id = ?
                  AND slug = ?
                """,
                OffsetDateTime.class,
                workspaceId,
                slug));
    }

    private void appendLifecycleClause(
            StringBuilder sql,
            List<Object> parameters,
            OffsetDateTime now,
            LinkLifecycleState state) {
        switch (state) {
            case ACTIVE -> {
                sql.append("""
                        
                          AND (p.expires_at IS NULL OR p.expires_at > ?)
                          AND p.lifecycle_state = 'ACTIVE'
                        """);
                parameters.add(now);
            }
            case SUSPENDED -> sql.append("""
                    
                      AND p.lifecycle_state = 'SUSPENDED'
                    """);
            case ARCHIVED -> sql.append("""
                    
                      AND p.lifecycle_state = 'ARCHIVED'
                    """);
            case EXPIRED -> {
                sql.append("""
                        
                          AND p.expires_at IS NOT NULL
                          AND p.expires_at <= ?
                          AND p.lifecycle_state = 'ACTIVE'
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

    private void appendAbuseClause(
            StringBuilder sql,
            List<Object> parameters,
            LinkAbuseStatus abuseStatus,
            String alias) {
        if (abuseStatus == null) {
            return;
        }
        String qualifiedColumn = (alias == null || alias.isBlank() ? "" : alias + ".") + "abuse_status";
        sql.append(" AND ").append(qualifiedColumn).append(" = ?");
        parameters.add(abuseStatus.name());
    }

    private void appendCatalogTagClause(StringBuilder sql, List<Object> parameters, String tag) {
        if (tag == null || tag.isBlank()) {
            return;
        }
        sql.append("""
                
                  AND LOWER(COALESCE(p.tags_json, '')) LIKE ?
                """);
        parameters.add("%\"" + tag + "\"%");
    }

    private void appendCatalogLifecycleClause(
            StringBuilder sql,
            List<Object> parameters,
            OffsetDateTime asOf,
            LinkLifecycleState lifecycle) {
        if (lifecycle == null || lifecycle == LinkLifecycleState.ALL) {
            return;
        }
        sql.append("""
                
                  AND p.deleted_at IS NULL
                """);
        switch (lifecycle) {
            case ACTIVE -> {
                sql.append("""
                        
                          AND p.lifecycle_state = 'ACTIVE'
                          AND (p.expires_at IS NULL OR p.expires_at > ?)
                        """);
                parameters.add(asOf);
            }
            case SUSPENDED -> sql.append("""
                    
                      AND p.lifecycle_state = 'SUSPENDED'
                    """);
            case ARCHIVED -> sql.append("""
                    
                      AND p.lifecycle_state = 'ARCHIVED'
                    """);
            case EXPIRED -> {
                sql.append("""
                        
                          AND p.lifecycle_state = 'ACTIVE'
                          AND p.expires_at IS NOT NULL
                          AND p.expires_at <= ?
                        """);
                parameters.add(asOf);
            }
            case ALL -> {
                // no-op
            }
        }
    }

    private void appendDiscoverySearchClause(StringBuilder sql, List<Object> parameters, String query) {
        if (query == null || query.isBlank()) {
            return;
        }
        sql.append("""
                
                  AND (
                    LOWER(d.slug) LIKE ?
                    OR LOWER(d.original_url) LIKE ?
                    OR LOWER(COALESCE(d.title, '')) LIKE ?
                    OR LOWER(COALESCE(d.tags_json, '')) LIKE ?
                    OR LOWER(COALESCE(d.hostname, '')) LIKE ?
                  )
                """);
        String pattern = "%" + query.toLowerCase(Locale.ROOT).trim() + "%";
        parameters.add(pattern);
        parameters.add(pattern);
        parameters.add(pattern);
        parameters.add(pattern);
        parameters.add(pattern);
    }

    private void appendDiscoveryHostnameClause(StringBuilder sql, List<Object> parameters, String hostname) {
        if (hostname == null || hostname.isBlank()) {
            return;
        }
        sql.append("""
                
                  AND LOWER(d.hostname) = ?
                """);
        parameters.add(hostname.trim().toLowerCase(Locale.ROOT));
    }

    private void appendDiscoveryTagClause(StringBuilder sql, List<Object> parameters, String tag) {
        if (tag == null || tag.isBlank()) {
            return;
        }
        sql.append("""
                
                  AND LOWER(COALESCE(d.tags_json, '')) LIKE ?
                """);
        parameters.add("%\"" + tag.trim().toLowerCase(Locale.ROOT) + "\"%");
    }

    private void appendDiscoveryLifecycleClause(
            StringBuilder sql,
            List<Object> parameters,
            OffsetDateTime now,
            LinkDiscoveryLifecycleFilter lifecycle) {
        switch (lifecycle) {
            case ACTIVE -> {
                sql.append("""
                        
                          AND d.deleted_at IS NULL
                          AND (d.expires_at IS NULL OR d.expires_at > ?)
                        """);
                parameters.add(now);
            }
            case EXPIRED -> {
                sql.append("""
                        
                          AND d.deleted_at IS NULL
                          AND d.expires_at IS NOT NULL
                          AND d.expires_at <= ?
                        """);
                parameters.add(now);
            }
            case DELETED -> sql.append("""
                        
                          AND d.deleted_at IS NOT NULL
                        """);
            case ALL -> {
                // no-op
            }
        }
    }

    private void appendDiscoveryExpirationClause(
            StringBuilder sql,
            List<Object> parameters,
            OffsetDateTime now,
            LinkDiscoveryExpirationFilter expiration) {
        switch (expiration) {
            case ANY -> {
                // no-op
            }
            case SCHEDULED -> sql.append("""
                        
                          AND d.expires_at IS NOT NULL
                        """);
            case NONE -> sql.append("""
                        
                          AND d.expires_at IS NULL
                        """);
            case EXPIRED -> {
                sql.append("""
                        
                          AND d.expires_at IS NOT NULL
                          AND d.expires_at <= ?
                        """);
                parameters.add(now);
            }
        }
    }

    private void appendDiscoveryCursorClause(
            StringBuilder sql,
            List<Object> parameters,
            LinkDiscoverySort sort,
            String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return;
        }
        DecodedCursor decodedCursor = decodeCursor(cursor);
        switch (sort) {
            case UPDATED_DESC -> {
                OffsetDateTime updatedAt = parseCursorTimestamp(decodedCursor.primaryValue());
                sql.append("""
                        
                          AND (d.updated_at < ? OR (d.updated_at = ? AND d.slug > ?))
                        """);
                parameters.add(updatedAt);
                parameters.add(updatedAt);
                parameters.add(decodedCursor.slug());
            }
            case CREATED_DESC -> {
                OffsetDateTime createdAt = parseCursorTimestamp(decodedCursor.primaryValue());
                sql.append("""
                        
                          AND (d.created_at < ? OR (d.created_at = ? AND d.slug > ?))
                        """);
                parameters.add(createdAt);
                parameters.add(createdAt);
                parameters.add(decodedCursor.slug());
            }
            case SLUG_ASC -> {
                sql.append("""
                        
                          AND d.slug > ?
                        """);
                parameters.add(decodedCursor.slug());
            }
        }
    }

    private String discoveryOrderBy(LinkDiscoverySort sort) {
        return switch (sort) {
            case UPDATED_DESC -> "d.updated_at DESC, d.slug ASC";
            case CREATED_DESC -> "d.created_at DESC, d.slug ASC";
            case SLUG_ASC -> "d.slug ASC";
        };
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
                    lifecycle_state = ?,
                    deleted_at = ?,
                    workspace_id = ?,
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
                linkLifecycleEvent.lifecycleState().name(),
                deletedAt,
                linkLifecycleEvent.workspaceId(),
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
                        slug, original_url, created_at, updated_at, title, tags_json, hostname, expires_at, lifecycle_state, deleted_at, version, owner_id, workspace_id
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    linkLifecycleEvent.slug(),
                    linkLifecycleEvent.originalUrl(),
                    linkLifecycleEvent.occurredAt(),
                    linkLifecycleEvent.occurredAt(),
                    linkLifecycleEvent.title(),
                    tagsJson,
                    linkLifecycleEvent.hostname(),
                    linkLifecycleEvent.expiresAt(),
                    linkLifecycleEvent.lifecycleState().name(),
                    deletedAt,
                    linkLifecycleEvent.version(),
                    linkLifecycleEvent.ownerId(),
                    linkLifecycleEvent.workspaceId());
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
                        lifecycle_state = ?,
                        deleted_at = ?,
                        workspace_id = ?,
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
                    linkLifecycleEvent.lifecycleState().name(),
                    deletedAt,
                    linkLifecycleEvent.workspaceId(),
                    linkLifecycleEvent.ownerId(),
                    linkLifecycleEvent.version(),
                    linkLifecycleEvent.slug(),
                    linkLifecycleEvent.version());
        }
    }

    private void upsertDiscoveryProjection(
            LinkLifecycleEvent linkLifecycleEvent,
            String tagsJson,
            OffsetDateTime deletedAt,
            String lifecycleState) {
        int updated = jdbcTemplate.update(
                """
                UPDATE link_discovery_projection
                SET owner_id = ?,
                    workspace_id = ?,
                    original_url = ?,
                    title = ?,
                    hostname = ?,
                    tags_json = ?,
                    updated_at = ?,
                    expires_at = ?,
                    deleted_at = ?,
                    lifecycle_state = ?,
                    version = ?
                WHERE slug = ?
                  AND version < ?
                """,
                linkLifecycleEvent.ownerId(),
                linkLifecycleEvent.workspaceId(),
                linkLifecycleEvent.originalUrl(),
                linkLifecycleEvent.title(),
                linkLifecycleEvent.hostname(),
                tagsJson,
                linkLifecycleEvent.occurredAt(),
                linkLifecycleEvent.expiresAt(),
                deletedAt,
                lifecycleState,
                linkLifecycleEvent.version(),
                linkLifecycleEvent.slug(),
                linkLifecycleEvent.version());
        if (updated == 1) {
            return;
        }

        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO link_discovery_projection (
                        slug, owner_id, workspace_id, original_url, title, hostname, tags_json, created_at, updated_at, expires_at, deleted_at, lifecycle_state, version
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    linkLifecycleEvent.slug(),
                    linkLifecycleEvent.ownerId(),
                    linkLifecycleEvent.workspaceId(),
                    linkLifecycleEvent.originalUrl(),
                    linkLifecycleEvent.title(),
                    linkLifecycleEvent.hostname(),
                    tagsJson,
                    linkLifecycleEvent.occurredAt(),
                    linkLifecycleEvent.occurredAt(),
                    linkLifecycleEvent.expiresAt(),
                    deletedAt,
                    lifecycleState,
                    linkLifecycleEvent.version());
        } catch (DuplicateKeyException exception) {
            jdbcTemplate.update(
                    """
                UPDATE link_discovery_projection
                SET owner_id = ?,
                    workspace_id = ?,
                    original_url = ?,
                        title = ?,
                        hostname = ?,
                        tags_json = ?,
                        updated_at = ?,
                        expires_at = ?,
                        deleted_at = ?,
                        lifecycle_state = ?,
                        version = ?
                    WHERE slug = ?
                      AND version < ?
                    """,
                    linkLifecycleEvent.ownerId(),
                    linkLifecycleEvent.workspaceId(),
                    linkLifecycleEvent.originalUrl(),
                    linkLifecycleEvent.title(),
                    linkLifecycleEvent.hostname(),
                    tagsJson,
                    linkLifecycleEvent.occurredAt(),
                    linkLifecycleEvent.expiresAt(),
                    deletedAt,
                    lifecycleState,
                    linkLifecycleEvent.version(),
                    linkLifecycleEvent.slug(),
                    linkLifecycleEvent.version());
        }
    }

    private void deleteProjectionRows(String tableName, Long ownerId, String slug) {
        deleteProjectionRows(tableName, null, ownerId, slug);
    }

    private void deleteProjectionRows(String tableName, Long workspaceId, Long ownerId, String slug) {
        if (workspaceId == null && ownerId == null && (slug == null || slug.isBlank())) {
            jdbcTemplate.update("DELETE FROM " + tableName);
            return;
        }
        StringBuilder sql = new StringBuilder("DELETE FROM " + tableName + " WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        if (workspaceId != null) {
            sql.append(" AND workspace_id = ?");
            parameters.add(workspaceId);
        }
        if (ownerId != null) {
            sql.append(" AND owner_id = ?");
            parameters.add(ownerId);
        }
        if (slug != null && !slug.isBlank()) {
            sql.append(" AND slug = ?");
            parameters.add(slug);
        }
        jdbcTemplate.update(sql.toString(), parameters.toArray());
    }

    private List<TopLinkTraffic> findTopLinksLast24Hours(
            OffsetDateTime since,
            long ownerId,
            int limit,
            String tag,
            LinkLifecycleState lifecycle,
            OffsetDateTime asOf) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.slug, p.original_url, COUNT(*) AS click_total
                FROM link_catalog_projection p
                JOIN link_clicks c ON c.slug = p.slug
                WHERE c.clicked_at >= ?
                  AND p.owner_id = ?
                """);
        List<Object> parameters = new ArrayList<>(List.of(since, ownerId));
        appendCatalogTagClause(sql, parameters, tag);
        appendCatalogLifecycleClause(sql, parameters, asOf, lifecycle);
        sql.append("""
                
                GROUP BY p.slug, p.original_url
                ORDER BY click_total DESC, p.slug ASC
                LIMIT ?
                """);
        parameters.add(limit);
        return queryJdbcTemplate.query(
                sql.toString(),
                (resultSet, rowNum) -> new TopLinkTraffic(
                        resultSet.getString("slug"),
                        resultSet.getString("original_url"),
                        resultSet.getLong("click_total")),
                parameters.toArray());
    }

    private List<TrendingLink> findTrendingLinksLast24Hours(
            OffsetDateTime now,
            int limit,
            long ownerId,
            String tag,
            LinkLifecycleState lifecycle,
            OffsetDateTime asOf) {
        OffsetDateTime currentStart = now.minusHours(24);
        OffsetDateTime previousStart = now.minusHours(48);

        return queryRangeTrendingLinks(
                """
                SELECT p.slug,
                       p.original_url,
                       COALESCE(current_window.click_total, 0) - COALESCE(previous_window.click_total, 0) AS click_growth,
                       COALESCE(current_window.click_total, 0) AS current_window_clicks,
                       COALESCE(previous_window.click_total, 0) AS previous_window_clicks
                FROM link_catalog_projection p
                LEFT JOIN (
                    SELECT c.slug, COUNT(*) AS click_total
                    FROM link_clicks c
                    JOIN link_catalog_projection p_current ON p_current.slug = c.slug
                    WHERE p_current.owner_id = ?
                      AND c.clicked_at >= ? AND c.clicked_at < ?
                    GROUP BY c.slug
                ) current_window ON current_window.slug = p.slug
                LEFT JOIN (
                    SELECT c.slug, COUNT(*) AS click_total
                    FROM link_clicks c
                    JOIN link_catalog_projection p_previous ON p_previous.slug = c.slug
                    WHERE p_previous.owner_id = ?
                      AND c.clicked_at >= ? AND c.clicked_at < ?
                    GROUP BY c.slug
                ) previous_window ON previous_window.slug = p.slug
                WHERE p.owner_id = ?
                """,
                List.of(ownerId, currentStart, now, ownerId, previousStart, currentStart, ownerId),
                limit,
                tag,
                lifecycle,
                asOf);
    }

    private List<TopLinkTraffic> findTopLinksLast7Days(
            LocalDate startDate,
            long ownerId,
            int limit,
            String tag,
            LinkLifecycleState lifecycle,
            OffsetDateTime asOf) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.slug, p.original_url, SUM(r.click_count) AS click_total
                FROM link_catalog_projection p
                JOIN link_click_daily_rollups r ON r.slug = p.slug
                WHERE r.rollup_date >= ?
                  AND p.owner_id = ?
                """);
        List<Object> parameters = new ArrayList<>(List.of(startDate, ownerId));
        appendCatalogTagClause(sql, parameters, tag);
        appendCatalogLifecycleClause(sql, parameters, asOf, lifecycle);
        sql.append("""
                
                GROUP BY p.slug, p.original_url
                ORDER BY click_total DESC, p.slug ASC
                LIMIT ?
                """);
        parameters.add(limit);
        return queryJdbcTemplate.query(
                sql.toString(),
                (resultSet, rowNum) -> new TopLinkTraffic(
                        resultSet.getString("slug"),
                        resultSet.getString("original_url"),
                        resultSet.getLong("click_total")),
                parameters.toArray());
    }

    @Override
    public List<DailyClickBucket> findRecentHourlyClickBuckets(String slug, OffsetDateTime since, long ownerId) {
        return queryJdbcTemplate.query(
                """
                SELECT DATE_TRUNC('hour', c.clicked_at) AS bucket_hour, COUNT(*) AS click_total
                FROM link_clicks c
                JOIN links l ON l.slug = c.slug
                WHERE c.slug = ?
                  AND c.clicked_at >= ?
                  AND l.owner_id = ?
                GROUP BY DATE_TRUNC('hour', c.clicked_at)
                ORDER BY bucket_hour ASC
                """,
                (resultSet, rowNum) -> new DailyClickBucket(
                        resultSet.getObject("bucket_hour", OffsetDateTime.class).toLocalDate(),
                        resultSet.getLong("click_total")),
                slug,
                since,
                ownerId);
    }

    @Override
    public List<TopReferrer> findTopReferrers(String slug, int limit, long ownerId) {
        return queryJdbcTemplate.query(
                """
                SELECT COALESCE(NULLIF(referrer, ''), 'direct') AS referrer, COUNT(*) AS click_total
                FROM link_clicks c
                JOIN links l ON l.slug = c.slug
                WHERE c.slug = ?
                  AND l.owner_id = ?
                GROUP BY COALESCE(NULLIF(referrer, ''), 'direct')
                ORDER BY click_total DESC, referrer ASC
                LIMIT ?
                """,
                (resultSet, rowNum) -> new TopReferrer(
                        resultSet.getString("referrer"),
                        resultSet.getLong("click_total")),
                slug,
                ownerId,
                limit);
    }

    @Override
    public OwnerTrafficTotals findOwnerTrafficTotals(
            OffsetDateTime last1HourSince,
            OffsetDateTime last24HoursSince,
            LocalDate last7DaysStartDate,
            long ownerId) {
        return queryJdbcTemplate.queryForObject(
                """
                SELECT
                    COALESCE(SUM(CASE WHEN c.clicked_at >= ? THEN 1 ELSE 0 END), 0) AS clicks_last_1_hour,
                    COALESCE(SUM(CASE WHEN c.clicked_at >= ? THEN 1 ELSE 0 END), 0) AS clicks_last_24_hours,
                    COALESCE(SUM(CASE WHEN CAST(c.clicked_at AS DATE) >= ? THEN 1 ELSE 0 END), 0) AS clicks_last_7_days
                FROM links l
                LEFT JOIN link_clicks c ON c.slug = l.slug
                WHERE l.owner_id = ?
                """,
                (resultSet, rowNum) -> new OwnerTrafficTotals(
                        resultSet.getLong("clicks_last_1_hour"),
                        resultSet.getLong("clicks_last_24_hours"),
                        resultSet.getLong("clicks_last_7_days")),
                last1HourSince,
                last24HoursSince,
                last7DaysStartDate,
                ownerId);
    }

    private List<TrendingLink> findTrendingLinksLast7Days(
            LocalDate today,
            int limit,
            long ownerId,
            String tag,
            LinkLifecycleState lifecycle,
            OffsetDateTime asOf) {
        LocalDate currentStart = today.minusDays(6);
        LocalDate previousStart = currentStart.minusDays(7);

        return queryRangeTrendingLinks(
                """
                SELECT p.slug,
                       p.original_url,
                       COALESCE(current_window.click_total, 0) - COALESCE(previous_window.click_total, 0) AS click_growth,
                       COALESCE(current_window.click_total, 0) AS current_window_clicks,
                       COALESCE(previous_window.click_total, 0) AS previous_window_clicks
                FROM link_catalog_projection p
                LEFT JOIN (
                    SELECT r.slug, SUM(r.click_count) AS click_total
                    FROM link_click_daily_rollups r
                    JOIN link_catalog_projection p_current ON p_current.slug = r.slug
                    WHERE p_current.owner_id = ?
                      AND r.rollup_date >= ?
                    GROUP BY r.slug
                ) current_window ON current_window.slug = p.slug
                LEFT JOIN (
                    SELECT r.slug, SUM(r.click_count) AS click_total
                    FROM link_click_daily_rollups r
                    JOIN link_catalog_projection p_previous ON p_previous.slug = r.slug
                    WHERE p_previous.owner_id = ?
                      AND r.rollup_date >= ?
                      AND r.rollup_date < ?
                    GROUP BY r.slug
                ) previous_window ON previous_window.slug = p.slug
                WHERE p.owner_id = ?
                """,
                List.of(ownerId, currentStart, ownerId, previousStart, currentStart, ownerId),
                limit,
                tag,
                lifecycle,
                asOf);
    }

    private List<TrendingLink> queryRangeTrendingLinks(
            String baseSql,
            List<Object> baseParameters,
            int limit,
            String tag,
            LinkLifecycleState lifecycle,
            OffsetDateTime asOf) {
        StringBuilder sql = new StringBuilder(baseSql);
        List<Object> parameters = new ArrayList<>(baseParameters);
        appendCatalogTagClause(sql, parameters, tag);
        appendCatalogLifecycleClause(sql, parameters, asOf, lifecycle);
        sql.append("""
                
                  AND COALESCE(current_window.click_total, 0) - COALESCE(previous_window.click_total, 0) > 0
                ORDER BY click_growth DESC, current_window_clicks DESC, p.slug ASC
                LIMIT ?
                """);
        parameters.add(limit);
        return queryJdbcTemplate.query(
                sql.toString(),
                (resultSet, rowNum) -> new TrendingLink(
                        resultSet.getString("slug"),
                        resultSet.getString("original_url"),
                        resultSet.getLong("click_growth"),
                        resultSet.getLong("current_window_clicks"),
                        resultSet.getLong("previous_window_clicks")),
                parameters.toArray());
    }

    private Link toLink(String slug, String originalUrl) {
        return new Link(new LinkSlug(slug), new OriginalUrl(originalUrl));
    }

    private long resolveLegacyOwnerId(long workspaceId) {
        Long ownerId = jdbcTemplate.queryForObject(
                "SELECT created_by_owner_id FROM workspaces WHERE id = ?",
                Long.class,
                workspaceId);
        if (ownerId == null) {
            throw new IllegalArgumentException("Workspace not found: " + workspaceId);
        }
        return ownerId;
    }

    private LinkDetails toLinkDetails(
            String slug,
            String originalUrl,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt,
            String title,
            String tagsJson,
            String hostname,
            LinkAbuseStatus abuseStatus,
            long version,
            long clickTotal) {
        return new LinkDetails(
                slug,
                originalUrl,
                createdAt,
                expiresAt,
                title,
                deserializeTags(tagsJson),
                hostname,
                abuseStatus,
                version,
                clickTotal);
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

    private LinkDiscoveryLifecycleState mapLifecycleState(
            OffsetDateTime deletedAt,
            OffsetDateTime expiresAt,
            OffsetDateTime now) {
        if (deletedAt != null) {
            return LinkDiscoveryLifecycleState.DELETED;
        }
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            return LinkDiscoveryLifecycleState.EXPIRED;
        }
        return LinkDiscoveryLifecycleState.ACTIVE;
    }

    private String determineLifecycleState(LinkLifecycleEvent event, OffsetDateTime now) {
        if (event.lifecycleState() == LinkLifecycleState.SUSPENDED) {
            return LinkDiscoveryLifecycleState.ACTIVE.name();
        }
        if (event.lifecycleState() == LinkLifecycleState.ARCHIVED) {
            return LinkDiscoveryLifecycleState.DELETED.name();
        }
        return switch (mapLifecycleState(
                event.eventType() == LinkLifecycleEventType.DELETED ? event.occurredAt() : null,
                event.expiresAt(),
                now)) {
            case ACTIVE -> LinkDiscoveryLifecycleState.ACTIVE.name();
            case EXPIRED -> LinkDiscoveryLifecycleState.EXPIRED.name();
            case DELETED -> LinkDiscoveryLifecycleState.DELETED.name();
        };
    }

    private String encodeCursor(LinkDiscoverySort sort, LinkDiscoveryItem item) {
        String value = switch (sort) {
            case UPDATED_DESC -> item.updatedAt() + "|" + item.slug();
            case CREATED_DESC -> item.createdAt() + "|" + item.slug();
            case SLUG_ASC -> item.slug();
        };
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private DecodedCursor decodeCursor(String cursor) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), java.nio.charset.StandardCharsets.UTF_8);
            int delimiterIndex = decoded.indexOf('|');
            if (delimiterIndex == -1) {
                return new DecodedCursor(decoded, decoded);
            }
            return new DecodedCursor(decoded.substring(0, delimiterIndex), decoded.substring(delimiterIndex + 1));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Cursor is invalid");
        }
    }

    private OffsetDateTime parseCursorTimestamp(String value) {
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Cursor is invalid");
        }
    }

    private record DecodedCursor(String primaryValue, String slug) {
    }
}
