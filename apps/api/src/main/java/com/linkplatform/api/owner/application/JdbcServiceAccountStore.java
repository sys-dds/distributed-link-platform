package com.linkplatform.api.owner.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcServiceAccountStore implements ServiceAccountStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcServiceAccountStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long createServiceAccountOwner(String ownerKey, String displayName, OffsetDateTime createdAt) {
        Long ownerId = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) + 1 FROM owners", Long.class);
        if (ownerId == null) {
            throw new IllegalStateException("Unable to allocate service account owner id");
        }
        jdbcTemplate.update(
                "INSERT INTO owners (id, owner_key, display_name, plan, created_at) VALUES (?, ?, ?, 'FREE', ?)",
                ownerId,
                ownerKey,
                displayName,
                createdAt);
        return ownerId;
    }

    @Override
    public ServiceAccountRecord create(
            long serviceAccountId,
            long workspaceId,
            String name,
            String slug,
            ServiceAccountStatus status,
            OffsetDateTime createdAt,
            long createdByOwnerId) {
        jdbcTemplate.update(
                """
                INSERT INTO service_accounts (id, workspace_id, name, slug, status, created_at, created_by_owner_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                serviceAccountId,
                workspaceId,
                name,
                slug,
                status.name(),
                createdAt,
                createdByOwnerId);
        return findById(serviceAccountId).orElseThrow();
    }

    @Override
    public Optional<ServiceAccountRecord> findById(long serviceAccountId) {
        return jdbcTemplate.query(
                        """
                        SELECT id, workspace_id, name, slug, status, created_at, created_by_owner_id, disabled_at, disabled_by_owner_id
                        FROM service_accounts
                        WHERE id = ?
                        """,
                        (resultSet, rowNum) -> mapRecord(resultSet),
                        serviceAccountId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<ServiceAccountRecord> findByWorkspaceIdAndId(long workspaceId, long serviceAccountId) {
        return jdbcTemplate.query(
                        """
                        SELECT id, workspace_id, name, slug, status, created_at, created_by_owner_id, disabled_at, disabled_by_owner_id
                        FROM service_accounts
                        WHERE workspace_id = ?
                          AND id = ?
                        """,
                        (resultSet, rowNum) -> mapRecord(resultSet),
                        workspaceId,
                        serviceAccountId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<ServiceAccountRecord> findByWorkspaceIdAndSlug(long workspaceId, String slug) {
        return jdbcTemplate.query(
                        """
                        SELECT id, workspace_id, name, slug, status, created_at, created_by_owner_id, disabled_at, disabled_by_owner_id
                        FROM service_accounts
                        WHERE workspace_id = ?
                          AND slug = ?
                        """,
                        (resultSet, rowNum) -> mapRecord(resultSet),
                        workspaceId,
                        slug)
                .stream()
                .findFirst();
    }

    @Override
    public List<ServiceAccountRecord> findByWorkspaceId(long workspaceId) {
        return jdbcTemplate.query(
                """
                SELECT id, workspace_id, name, slug, status, created_at, created_by_owner_id, disabled_at, disabled_by_owner_id
                FROM service_accounts
                WHERE workspace_id = ?
                ORDER BY created_at DESC, id DESC
                """,
                (resultSet, rowNum) -> mapRecord(resultSet),
                workspaceId);
    }

    @Override
    public boolean disable(long serviceAccountId, OffsetDateTime disabledAt, long disabledByOwnerId) {
        return jdbcTemplate.update(
                """
                UPDATE service_accounts
                SET status = 'DISABLED',
                    disabled_at = ?,
                    disabled_by_owner_id = ?
                WHERE id = ?
                  AND status = 'ACTIVE'
                """,
                disabledAt,
                disabledByOwnerId,
                serviceAccountId) == 1;
    }

    private ServiceAccountRecord mapRecord(ResultSet resultSet) throws SQLException {
        return new ServiceAccountRecord(
                resultSet.getLong("id"),
                resultSet.getLong("workspace_id"),
                resultSet.getString("name"),
                resultSet.getString("slug"),
                ServiceAccountStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getLong("created_by_owner_id"),
                resultSet.getObject("disabled_at", OffsetDateTime.class),
                getNullableLong(resultSet, "disabled_by_owner_id"));
    }

    private Long getNullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }
}
