package com.linkplatform.api.link.application;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcLinkAbuseStore implements LinkAbuseStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcLinkAbuseStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public LinkAbuseCaseRecord openOrIncrementCase(
            long workspaceId,
            String slug,
            LinkAbuseSource source,
            int riskScore,
            String summary,
            String detailSummary,
            String targetHost,
            Long createdByOwnerId,
            OffsetDateTime now) {
        List<LinkAbuseCaseRecord> openCases = jdbcTemplate.query(
                """
                SELECT *
                FROM link_abuse_cases
                WHERE workspace_id = ?
                  AND slug = ?
                  AND source = ?
                  AND status = 'OPEN'
                ORDER BY updated_at DESC, id DESC
                """,
                (resultSet, rowNum) -> mapRecord(resultSet),
                workspaceId,
                slug,
                source.name());
        if (!openCases.isEmpty()) {
            LinkAbuseCaseRecord existing = openCases.get(0);
            jdbcTemplate.update(
                    """
                    UPDATE link_abuse_cases
                    SET signal_count = ?,
                        risk_score = ?,
                        summary = ?,
                        detail_summary = ?,
                        target_host = ?,
                        updated_at = ?
                    WHERE id = ?
                    """,
                    existing.signalCount() + 1,
                    Math.max(existing.riskScore(), riskScore),
                    shorten(summary, 255),
                    shorten(detailSummary, 1024),
                    shorten(targetHost, 255),
                    now,
                    existing.id());
            return findCaseById(workspaceId, existing.id()).orElseThrow(() -> new AbuseCaseNotFoundException(existing.id()));
        }

        jdbcTemplate.update(
                """
                INSERT INTO link_abuse_cases (
                    workspace_id, slug, status, source, signal_count, risk_score, summary, detail_summary,
                    target_host, created_at, updated_at, created_by_owner_id
                ) VALUES (?, ?, 'OPEN', ?, 1, ?, ?, ?, ?, ?, ?, ?)
                """,
                workspaceId,
                slug,
                source.name(),
                riskScore,
                shorten(summary, 255),
                shorten(detailSummary, 1024),
                shorten(targetHost, 255),
                now,
                now,
                createdByOwnerId);
        Long createdId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM link_abuse_cases WHERE workspace_id = ? AND slug = ? AND source = ?",
                Long.class,
                workspaceId,
                slug,
                source.name());
        if (createdId == null) {
            throw new IllegalStateException("Abuse case insert failed");
        }
        return findCaseById(workspaceId, createdId).orElseThrow(() -> new AbuseCaseNotFoundException(createdId));
    }

    @Override
    public Optional<LinkAbuseCaseRecord> findCaseById(long workspaceId, long caseId) {
        return jdbcTemplate.query(
                        "SELECT * FROM link_abuse_cases WHERE workspace_id = ? AND id = ?",
                        (resultSet, rowNum) -> mapRecord(resultSet),
                        workspaceId,
                        caseId)
                .stream()
                .findFirst();
    }

    @Override
    public List<LinkAbuseCaseRecord> findQueue(long workspaceId, LinkAbuseQueueQuery query) {
        StringBuilder sql = new StringBuilder("""
                SELECT *
                FROM link_abuse_cases
                WHERE workspace_id = ?
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(workspaceId);
        if (query.status() != null) {
            sql.append(" AND status = ?");
            parameters.add(query.status().name());
        }
        if (query.source() != null) {
            sql.append(" AND source = ?");
            parameters.add(query.source().name());
        }
        if (query.slug() != null && !query.slug().isBlank()) {
            sql.append(" AND slug = ?");
            parameters.add(query.slug().trim());
        }
        DecodedCursor cursor = decodeCursor(query.cursor());
        if (cursor != null) {
            sql.append("""
                     AND (
                         updated_at < ?
                         OR (updated_at = ? AND id < ?)
                     )
                    """);
            parameters.add(cursor.updatedAt());
            parameters.add(cursor.updatedAt());
            parameters.add(cursor.id());
        }
        sql.append(" ORDER BY updated_at DESC, id DESC LIMIT ?");
        parameters.add(query.limit() + 1);
        return jdbcTemplate.query(sql.toString(), (resultSet, rowNum) -> mapRecord(resultSet), parameters.toArray());
    }

    @Override
    public Optional<LinkScopeRecord> findLinkScope(String slug) {
        return jdbcTemplate.query(
                        """
                        SELECT workspace_id, owner_id, slug, hostname, abuse_status
                        FROM links
                        WHERE slug = ?
                        """,
                        (resultSet, rowNum) -> new LinkScopeRecord(
                                resultSet.getLong("workspace_id"),
                                resultSet.getLong("owner_id"),
                                resultSet.getString("slug"),
                                resultSet.getString("hostname"),
                                LinkAbuseStatus.valueOf(resultSet.getString("abuse_status"))),
                        slug)
                .stream()
                .findFirst();
    }

    @Override
    public long countOpenCases(long workspaceId, String slug) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM link_abuse_cases
                WHERE workspace_id = ?
                  AND slug = ?
                  AND status = 'OPEN'
                """,
                Long.class,
                workspaceId,
                slug);
        return count == null ? 0L : count;
    }

    @Override
    public boolean resolveCase(
            long workspaceId,
            long caseId,
            LinkAbuseCaseStatus currentStatus,
            LinkAbuseCaseStatus nextStatus,
            String resolution,
            Long reviewedByOwnerId,
            String resolutionNote,
            OffsetDateTime now) {
        return jdbcTemplate.update(
                """
                UPDATE link_abuse_cases
                SET status = ?,
                    reviewed_at = ?,
                    reviewed_by_owner_id = ?,
                    resolution = ?,
                    resolution_note = ?,
                    updated_at = ?
                WHERE workspace_id = ?
                  AND id = ?
                  AND status = ?
                """,
                nextStatus.name(),
                now,
                reviewedByOwnerId,
                resolution,
                shorten(resolutionNote, 512),
                now,
                workspaceId,
                caseId,
                currentStatus.name()) == 1;
    }

    public static String encodeCursor(LinkAbuseCaseRecord record) {
        String value = record.updatedAt() + "|" + record.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private LinkAbuseCaseRecord mapRecord(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new LinkAbuseCaseRecord(
                resultSet.getLong("id"),
                resultSet.getLong("workspace_id"),
                resultSet.getString("slug"),
                LinkAbuseCaseStatus.valueOf(resultSet.getString("status")),
                LinkAbuseSource.valueOf(resultSet.getString("source")),
                resultSet.getLong("signal_count"),
                resultSet.getInt("risk_score"),
                resultSet.getString("summary"),
                resultSet.getString("detail_summary"),
                resultSet.getString("target_host"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class),
                resultSet.getObject("created_by_owner_id", Long.class),
                resultSet.getObject("reviewed_at", OffsetDateTime.class),
                resultSet.getObject("reviewed_by_owner_id", Long.class),
                resultSet.getString("resolution"),
                resultSet.getString("resolution_note"));
    }

    private String shorten(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private DecodedCursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int delimiterIndex = decoded.lastIndexOf('|');
            if (delimiterIndex <= 0 || delimiterIndex == decoded.length() - 1) {
                throw new IllegalArgumentException("Cursor is invalid");
            }
            return new DecodedCursor(
                    OffsetDateTime.parse(decoded.substring(0, delimiterIndex)),
                    Long.parseLong(decoded.substring(delimiterIndex + 1)));
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new IllegalArgumentException("Cursor is invalid");
        }
    }

    private record DecodedCursor(OffsetDateTime updatedAt, long id) {
    }
}
