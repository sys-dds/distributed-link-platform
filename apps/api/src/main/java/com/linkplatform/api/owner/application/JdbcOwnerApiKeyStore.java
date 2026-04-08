package com.linkplatform.api.owner.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

@Component
public class JdbcOwnerApiKeyStore implements OwnerApiKeyStore {

    private static final TypeReference<List<String>> SCOPE_LIST_TYPE = new TypeReference<>() {
    };
    private static final Pattern SCOPE_VALUE_PATTERN = Pattern.compile("[a-z_]+:[a-z]+");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcOwnerApiKeyStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public OwnerApiKeyRecord create(
            long ownerId,
            long workspaceId,
            String keyPrefix,
            String keyHash,
            String label,
            Set<ApiKeyScope> scopes,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt,
            String createdBy) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    """
                    INSERT INTO owner_api_keys (
                        owner_id, workspace_id, key_prefix, key_hash, key_label, label, scopes_json, created_at, expires_at, created_by
                    ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)
                    """,
                    new String[] {"id"});
            statement.setLong(1, ownerId);
            statement.setLong(2, workspaceId);
            statement.setString(3, keyPrefix);
            statement.setString(4, keyHash);
            statement.setString(5, label);
            statement.setString(6, label);
            statement.setString(7, serializeScopes(scopes));
            statement.setObject(8, createdAt);
            statement.setObject(9, expiresAt);
            statement.setString(10, createdBy);
            return statement;
        }, keyHolder);
        Number id = keyHolder.getKey();
        return findById(workspaceId, id.longValue()).orElseThrow();
    }

    @Override
    public List<OwnerApiKeyRecord> findByWorkspaceId(long workspaceId) {
        return jdbcTemplate.query(
                baseSelect() + " WHERE k.workspace_id = ? ORDER BY k.created_at DESC, k.id DESC",
                this::mapRecord,
                workspaceId);
    }

    @Override
    public List<OwnerApiKeyRecord> findActiveByWorkspaceId(long workspaceId, OffsetDateTime now) {
        return jdbcTemplate.query(
                baseSelect() + """
                         WHERE k.workspace_id = ?
                           AND k.revoked_at IS NULL
                           AND (k.expires_at IS NULL OR k.expires_at > ?)
                        """,
                this::mapRecord,
                workspaceId,
                now);
    }

    @Override
    public Optional<OwnerApiKeyRecord> findById(long workspaceId, long keyId) {
        return jdbcTemplate.query(
                        baseSelect() + " WHERE k.workspace_id = ? AND k.id = ?",
                        this::mapRecord,
                        workspaceId,
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
    public void revoke(long workspaceId, long keyId, OffsetDateTime revokedAt, String revokedBy) {
        jdbcTemplate.update(
                """
                UPDATE owner_api_keys
                SET revoked_at = ?, revoked_by = ?
                WHERE workspace_id = ? AND id = ? AND revoked_at IS NULL
                """,
                revokedAt,
                revokedBy,
                workspaceId,
                keyId);
    }

    @Override
    public void expire(long workspaceId, long keyId, OffsetDateTime expiresAt, String revokedBy) {
        jdbcTemplate.update(
                """
                UPDATE owner_api_keys
                SET expires_at = ?, revoked_by = COALESCE(revoked_by, ?)
                WHERE workspace_id = ? AND id = ?
                """,
                expiresAt,
                revokedBy,
                workspaceId,
                keyId);
    }

    @Override
    public void touchLastUsed(long keyId, OffsetDateTime lastUsedAt) {
        jdbcTemplate.update("UPDATE owner_api_keys SET last_used_at = ? WHERE id = ?", lastUsedAt, keyId);
    }

    @Override
    public void lockWorkspace(long workspaceId) {
        jdbcTemplate.queryForObject("SELECT id FROM workspaces WHERE id = ? FOR UPDATE", Long.class, workspaceId);
    }

    private String baseSelect() {
        return """
                SELECT k.id,
                       k.owner_id,
                       o.owner_key,
                       o.plan,
                       k.workspace_id,
                       w.slug AS workspace_slug,
                       k.key_prefix,
                       k.key_hash,
                       k.label,
                       k.scopes_json,
                       k.created_at,
                       k.last_used_at,
                       k.revoked_at,
                       k.expires_at,
                       k.created_by,
                       k.revoked_by
                FROM owner_api_keys k
                JOIN owners o ON o.id = k.owner_id
                JOIN workspaces w ON w.id = k.workspace_id
                """;
    }

    private OwnerApiKeyRecord mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new OwnerApiKeyRecord(
                resultSet.getLong("id"),
                resultSet.getLong("owner_id"),
                resultSet.getString("owner_key"),
                OwnerPlan.valueOf(resultSet.getString("plan")),
                resultSet.getLong("workspace_id"),
                resultSet.getString("workspace_slug"),
                resultSet.getString("key_prefix"),
                resultSet.getString("key_hash"),
                resultSet.getString("label"),
                deserializeScopes(resultSet.getString("scopes_json")),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("last_used_at", OffsetDateTime.class),
                resultSet.getObject("revoked_at", OffsetDateTime.class),
                resultSet.getObject("expires_at", OffsetDateTime.class),
                resultSet.getString("created_by"),
                resultSet.getString("revoked_by"));
    }

    private String serializeScopes(Set<ApiKeyScope> scopes) {
        try {
            return objectMapper.writeValueAsString(scopes.stream().map(ApiKeyScope::value).toList());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("API key scopes could not be serialized", exception);
        }
    }

    private Set<ApiKeyScope> deserializeScopes(String scopesJson) {
        if (scopesJson == null || scopesJson.isBlank()) {
            return Set.of();
        }
        try {
            return parseScopesJson(scopesJson);
        } catch (JsonProcessingException exception) {
            String normalized = normalizeScopesJson(scopesJson);
            try {
                return parseScopesJson(normalized);
            } catch (JsonProcessingException normalizedException) {
                Set<ApiKeyScope> extractedScopes = extractScopes(normalized);
                if (!extractedScopes.isEmpty()) {
                    return extractedScopes;
                }
                throw new IllegalArgumentException("API key scopes could not be deserialized", normalizedException);
            }
        }
    }

    private Set<ApiKeyScope> parseScopesJson(String scopesJson) throws JsonProcessingException {
        return objectMapper.readValue(scopesJson, SCOPE_LIST_TYPE).stream()
                .map(ApiKeyScope::fromValue)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private String normalizeScopesJson(String scopesJson) {
        String normalized = scopesJson.trim();
        if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() >= 2) {
            normalized = normalized.substring(1, normalized.length() - 1)
                    .replace("\\\"", "\"");
        }
        return normalized.replace("\"\"", "\"");
    }

    private Set<ApiKeyScope> extractScopes(String scopesJson) {
        Matcher matcher = SCOPE_VALUE_PATTERN.matcher(scopesJson);
        EnumSet<ApiKeyScope> scopes = EnumSet.noneOf(ApiKeyScope.class);
        while (matcher.find()) {
            scopes.add(ApiKeyScope.fromValue(matcher.group()));
        }
        return scopes;
    }
}
