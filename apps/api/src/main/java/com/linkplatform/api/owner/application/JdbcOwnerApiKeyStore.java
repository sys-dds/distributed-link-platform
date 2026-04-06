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
public class JdbcOwnerApiKeyStore implements OwnerApiKeyStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcOwnerApiKeyStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public OwnerApiKeyRecord create(
            long ownerId,
            String keyPrefix,
            String keyHash,
            String label,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt,
            String createdBy) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    """
                    INSERT INTO owner_api_keys (
                        owner_id, key_prefix, key_hash, label, created_at, expires_at, created_by
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    new String[] {"id"});
            statement.setLong(1, ownerId);
            statement.setString(2, keyPrefix);
            statement.setString(3, keyHash);
            statement.setString(4, label);
            statement.setObject(5, createdAt);
            statement.setObject(6, expiresAt);
            statement.setString(7, createdBy);
            return statement;
        }, keyHolder);
        Number id = keyHolder.getKey();
        return findById(ownerId, id.longValue()).orElseThrow();
    }

    @Override
    public List<OwnerApiKeyRecord> findByOwnerId(long ownerId) {
        return jdbcTemplate.query(
                baseSelect() + " WHERE k.owner_id = ? ORDER BY k.created_at DESC, k.id DESC",
                this::mapRecord,
                ownerId);
    }

    @Override
    public List<OwnerApiKeyRecord> findActiveByOwnerId(long ownerId, OffsetDateTime now) {
        return jdbcTemplate.query(
                baseSelect() + """
                         WHERE k.owner_id = ?
                           AND k.revoked_at IS NULL
                           AND (k.expires_at IS NULL OR k.expires_at > ?)
                        """,
                this::mapRecord,
                ownerId,
                now);
    }

    @Override
    public Optional<OwnerApiKeyRecord> findById(long ownerId, long keyId) {
        return jdbcTemplate.query(
                        baseSelect() + " WHERE k.owner_id = ? AND k.id = ?",
                        this::mapRecord,
                        ownerId,
                        keyId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<OwnerApiKeyRecord> findActiveByHash(String keyHash, OffsetDateTime now) {
        return jdbcTemplate.query(
                        baseSelect() + """
                                 WHERE k.key_hash = ?
                                   AND k.revoked_at IS NULL
                                   AND (k.expires_at IS NULL OR k.expires_at > ?)
                                """,
                        this::mapRecord,
                        keyHash,
                        now)
                .stream()
                .findFirst();
    }

    @Override
    public void revoke(long ownerId, long keyId, OffsetDateTime revokedAt, String revokedBy) {
        jdbcTemplate.update(
                """
                UPDATE owner_api_keys
                SET revoked_at = ?, revoked_by = ?
                WHERE owner_id = ? AND id = ? AND revoked_at IS NULL
                """,
                revokedAt,
                revokedBy,
                ownerId,
                keyId);
    }

    @Override
    public void expire(long ownerId, long keyId, OffsetDateTime expiresAt, String revokedBy) {
        jdbcTemplate.update(
                """
                UPDATE owner_api_keys
                SET expires_at = ?, revoked_by = COALESCE(revoked_by, ?)
                WHERE owner_id = ? AND id = ?
                """,
                expiresAt,
                revokedBy,
                ownerId,
                keyId);
    }

    @Override
    public void touchLastUsed(long keyId, OffsetDateTime lastUsedAt) {
        jdbcTemplate.update("UPDATE owner_api_keys SET last_used_at = ? WHERE id = ?", lastUsedAt, keyId);
    }

    @Override
    public void lockOwner(long ownerId) {
        jdbcTemplate.queryForObject("SELECT id FROM owners WHERE id = ? FOR UPDATE", Long.class, ownerId);
    }

    private String baseSelect() {
        return """
                SELECT k.id,
                       k.owner_id,
                       o.owner_key,
                       o.plan,
                       k.key_prefix,
                       k.key_hash,
                       k.label,
                       k.created_at,
                       k.last_used_at,
                       k.revoked_at,
                       k.expires_at,
                       k.created_by,
                       k.revoked_by
                FROM owner_api_keys k
                JOIN owners o ON o.id = k.owner_id
                """;
    }

    private OwnerApiKeyRecord mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new OwnerApiKeyRecord(
                resultSet.getLong("id"),
                resultSet.getLong("owner_id"),
                resultSet.getString("owner_key"),
                OwnerPlan.valueOf(resultSet.getString("plan")),
                resultSet.getString("key_prefix"),
                resultSet.getString("key_hash"),
                resultSet.getString("label"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("last_used_at", OffsetDateTime.class),
                resultSet.getObject("revoked_at", OffsetDateTime.class),
                resultSet.getObject("expires_at", OffsetDateTime.class),
                resultSet.getString("created_by"),
                resultSet.getString("revoked_by"));
    }
}
