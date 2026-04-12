package com.linkplatform.api.runtime;

import java.time.OffsetDateTime;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcQueryReplicaRuntimeStore implements QueryReplicaRuntimeStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcQueryReplicaRuntimeStore(@Qualifier("dataSource") DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public Optional<QueryReplicaRuntimeState> findByName(String replicaName) {
        return jdbcTemplate.query(
                        """
                        SELECT replica_name, enabled, last_heartbeat_at, last_replica_visible_event_at,
                               last_fallback_at, last_fallback_reason, lag_seconds, updated_at
                        FROM query_replica_runtime_state
                        WHERE replica_name = ?
                        """,
                        (resultSet, rowNum) -> new QueryReplicaRuntimeState(
                                resultSet.getString("replica_name"),
                                resultSet.getBoolean("enabled"),
                                resultSet.getObject("last_heartbeat_at", OffsetDateTime.class),
                                resultSet.getObject("last_replica_visible_event_at", OffsetDateTime.class),
                                resultSet.getObject("last_fallback_at", OffsetDateTime.class),
                                resultSet.getString("last_fallback_reason"),
                                resultSet.getObject("lag_seconds", Long.class),
                                resultSet.getObject("updated_at", OffsetDateTime.class)),
                        replicaName)
                .stream()
                .findFirst();
    }

    @Override
    public void recordProbe(
            String replicaName,
            boolean enabled,
            QueryReplicaProbeResult probeResult,
            OffsetDateTime refreshedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO query_replica_runtime_state (
                    replica_name,
                    enabled,
                    last_heartbeat_at,
                    updated_at
                ) VALUES (?, ?, ?, ?)
                ON CONFLICT (replica_name) DO UPDATE
                SET enabled = EXCLUDED.enabled,
                    last_heartbeat_at = CASE
                        WHEN ? THEN EXCLUDED.last_heartbeat_at
                        ELSE query_replica_runtime_state.last_heartbeat_at
                    END,
                    updated_at = EXCLUDED.updated_at
                """,
                replicaName,
                enabled,
                probeResult.successful() ? refreshedAt : null,
                refreshedAt,
                probeResult.successful());
    }

    @Override
    public void recordFallback(
            String replicaName,
            String fallbackReason,
            String requestPath,
            Long workspaceId,
            OffsetDateTime triggeredAt,
            boolean appendLog) {
        jdbcTemplate.update(
                """
                UPDATE query_replica_runtime_state
                SET last_fallback_at = ?,
                    last_fallback_reason = ?,
                    updated_at = ?
                WHERE replica_name = ?
                """,
                triggeredAt,
                fallbackReason,
                triggeredAt,
                replicaName);
        if (appendLog) {
            jdbcTemplate.update(
                    """
                    INSERT INTO query_replica_fallback_log (
                        replica_name, fallback_reason, request_path, workspace_id, triggered_at
                    ) VALUES (?, ?, ?, ?, ?)
                    """,
                    replicaName,
                    fallbackReason,
                    requestPath,
                    workspaceId,
                    triggeredAt);
        }
    }
}
