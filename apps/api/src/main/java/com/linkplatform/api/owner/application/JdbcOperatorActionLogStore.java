package com.linkplatform.api.owner.application;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcOperatorActionLogStore implements OperatorActionLogStore {

    private static final int NOTE_MAX_LENGTH = 512;

    private final JdbcTemplate jdbcTemplate;

    public JdbcOperatorActionLogStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(
            Long workspaceId,
            long ownerId,
            String subsystem,
            String actionType,
            String targetSlug,
            Long targetCaseId,
            Long targetProjectionJobId,
            String note,
            OffsetDateTime createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO operator_action_log (
                    workspace_id, owner_id, subsystem, action_type, target_slug,
                    target_case_id, target_projection_job_id, note, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                workspaceId,
                ownerId,
                subsystem,
                actionType,
                shorten(targetSlug, 255),
                targetCaseId,
                targetProjectionJobId,
                shorten(note, NOTE_MAX_LENGTH),
                createdAt);
    }

    @Override
    public List<OperatorActionLogRecord> findRecent(Long workspaceId, OperatorActionLogQuery query) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, workspace_id, owner_id, subsystem, action_type, target_slug,
                       target_case_id, target_projection_job_id, note, created_at
                FROM operator_action_log
                WHERE 1 = 1
                """);
        List<Object> parameters = new ArrayList<>();
        if (workspaceId == null) {
            sql.append(" AND workspace_id IS NULL");
        } else {
            sql.append(" AND workspace_id = ?");
            parameters.add(workspaceId);
        }
        if (query.subsystem() != null && !query.subsystem().isBlank()) {
            sql.append(" AND subsystem = ?");
            parameters.add(query.subsystem().trim());
        }
        DecodedCursor cursor = decodeCursor(query.cursor());
        if (cursor != null) {
            sql.append("""
                     AND (
                         created_at < ?
                         OR (created_at = ? AND id < ?)
                     )
                    """);
            parameters.add(cursor.createdAt());
            parameters.add(cursor.createdAt());
            parameters.add(cursor.id());
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ?");
        parameters.add(query.limit() + 1);
        return jdbcTemplate.query(sql.toString(), (resultSet, rowNum) -> new OperatorActionLogRecord(
                        resultSet.getLong("id"),
                        resultSet.getObject("workspace_id", Long.class),
                        resultSet.getLong("owner_id"),
                        resultSet.getString("subsystem"),
                        resultSet.getString("action_type"),
                        resultSet.getString("target_slug"),
                        resultSet.getObject("target_case_id", Long.class),
                        resultSet.getObject("target_projection_job_id", Long.class),
                        resultSet.getString("note"),
                        resultSet.getObject("created_at", OffsetDateTime.class)),
                parameters.toArray());
    }

    public static String encodeCursor(OperatorActionLogRecord record) {
        String value = record.createdAt() + "|" + record.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String sanitizeNote(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() <= NOTE_MAX_LENGTH ? normalized : normalized.substring(0, NOTE_MAX_LENGTH);
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

    private record DecodedCursor(OffsetDateTime createdAt, long id) {
    }
}
