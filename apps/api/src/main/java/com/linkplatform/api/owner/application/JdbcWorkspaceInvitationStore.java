package com.linkplatform.api.owner.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

@Component
public class JdbcWorkspaceInvitationStore implements WorkspaceInvitationStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkspaceInvitationStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public WorkspaceInvitationRecord create(
            long workspaceId,
            String email,
            WorkspaceRole role,
            String tokenHash,
            String tokenPrefix,
            WorkspaceInvitationStatus status,
            OffsetDateTime expiresAt,
            OffsetDateTime createdAt,
            long createdByOwnerId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    """
                    INSERT INTO workspace_invitations (
                        workspace_id, email, role, token_hash, token_prefix, status, expires_at, created_at, created_by_owner_id
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    new String[] {"id"});
            statement.setLong(1, workspaceId);
            statement.setString(2, email);
            statement.setString(3, role.name());
            statement.setString(4, tokenHash);
            statement.setString(5, tokenPrefix);
            statement.setString(6, status.name());
            statement.setObject(7, expiresAt);
            statement.setObject(8, createdAt);
            statement.setLong(9, createdByOwnerId);
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    @Override
    public Optional<WorkspaceInvitationRecord> findById(long invitationId) {
        return jdbcTemplate.query(
                        """
                        SELECT id, workspace_id, email, role, token_hash, token_prefix, status, expires_at, created_at,
                               created_by_owner_id, accepted_at, accepted_by_owner_id, revoked_at, revoked_by_owner_id
                        FROM workspace_invitations
                        WHERE id = ?
                        """,
                        (resultSet, rowNum) -> mapRecord(resultSet),
                        invitationId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<WorkspaceInvitationRecord> findPendingByTokenHash(String tokenHash) {
        return jdbcTemplate.query(
                        """
                        SELECT id, workspace_id, email, role, token_hash, token_prefix, status, expires_at, created_at,
                               created_by_owner_id, accepted_at, accepted_by_owner_id, revoked_at, revoked_by_owner_id
                        FROM workspace_invitations
                        WHERE token_hash = ?
                          AND status = 'PENDING'
                        ORDER BY created_at DESC, id DESC
                        LIMIT 1
                        """,
                        (resultSet, rowNum) -> mapRecord(resultSet),
                        tokenHash)
                .stream()
                .findFirst();
    }

    @Override
    public List<WorkspaceInvitationRecord> findByWorkspaceId(long workspaceId) {
        return jdbcTemplate.query(
                """
                SELECT id, workspace_id, email, role, token_hash, token_prefix, status, expires_at, created_at,
                       created_by_owner_id, accepted_at, accepted_by_owner_id, revoked_at, revoked_by_owner_id
                FROM workspace_invitations
                WHERE workspace_id = ?
                ORDER BY created_at DESC, id DESC
                """,
                (resultSet, rowNum) -> mapRecord(resultSet),
                workspaceId);
    }

    @Override
    public boolean markAccepted(long invitationId, OffsetDateTime acceptedAt, long acceptedByOwnerId) {
        return jdbcTemplate.update(
                """
                UPDATE workspace_invitations
                SET status = 'ACCEPTED',
                    accepted_at = ?,
                    accepted_by_owner_id = ?
                WHERE id = ?
                  AND status = 'PENDING'
                """,
                acceptedAt,
                acceptedByOwnerId,
                invitationId) == 1;
    }

    @Override
    public boolean markRevoked(long invitationId, OffsetDateTime revokedAt, long revokedByOwnerId) {
        return jdbcTemplate.update(
                """
                UPDATE workspace_invitations
                SET status = 'REVOKED',
                    revoked_at = ?,
                    revoked_by_owner_id = ?
                WHERE id = ?
                  AND status = 'PENDING'
                """,
                revokedAt,
                revokedByOwnerId,
                invitationId) == 1;
    }

    @Override
    public boolean markExpired(long invitationId) {
        return jdbcTemplate.update(
                """
                UPDATE workspace_invitations
                SET status = 'EXPIRED'
                WHERE id = ?
                  AND status = 'PENDING'
                """,
                invitationId) == 1;
    }

    private WorkspaceInvitationRecord mapRecord(ResultSet resultSet) throws SQLException {
        return new WorkspaceInvitationRecord(
                resultSet.getLong("id"),
                resultSet.getLong("workspace_id"),
                resultSet.getString("email"),
                WorkspaceRole.valueOf(resultSet.getString("role")),
                resultSet.getString("token_hash"),
                resultSet.getString("token_prefix"),
                WorkspaceInvitationStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("expires_at", OffsetDateTime.class),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getLong("created_by_owner_id"),
                resultSet.getObject("accepted_at", OffsetDateTime.class),
                getNullableLong(resultSet, "accepted_by_owner_id"),
                resultSet.getObject("revoked_at", OffsetDateTime.class),
                getNullableLong(resultSet, "revoked_by_owner_id"));
    }

    private Long getNullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }
}
