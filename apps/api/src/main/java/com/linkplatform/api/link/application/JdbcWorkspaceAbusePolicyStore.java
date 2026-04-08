package com.linkplatform.api.link.application;

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
public class JdbcWorkspaceAbusePolicyStore implements WorkspaceAbusePolicyStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkspaceAbusePolicyStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<WorkspaceAbusePolicyRecord> findPolicy(long workspaceId) {
        return jdbcTemplate.query(
                        """
                        SELECT workspace_id, raw_ip_review_enabled, punycode_review_enabled,
                               repeated_host_quarantine_threshold, redirect_rate_limit_quarantine_threshold,
                               updated_at, updated_by_owner_id
                        FROM workspace_abuse_policies
                        WHERE workspace_id = ?
                        """,
                        this::mapPolicy,
                        workspaceId)
                .stream()
                .findFirst();
    }

    @Override
    public WorkspaceAbusePolicyRecord upsertPolicy(
            long workspaceId,
            boolean rawIpReviewEnabled,
            boolean punycodeReviewEnabled,
            int repeatedHostQuarantineThreshold,
            int redirectRateLimitQuarantineThreshold,
            OffsetDateTime updatedAt,
            long updatedByOwnerId) {
        jdbcTemplate.update(
                """
                INSERT INTO workspace_abuse_policies (
                    workspace_id, raw_ip_review_enabled, punycode_review_enabled,
                    repeated_host_quarantine_threshold, redirect_rate_limit_quarantine_threshold,
                    updated_at, updated_by_owner_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (workspace_id) DO UPDATE
                SET raw_ip_review_enabled = EXCLUDED.raw_ip_review_enabled,
                    punycode_review_enabled = EXCLUDED.punycode_review_enabled,
                    repeated_host_quarantine_threshold = EXCLUDED.repeated_host_quarantine_threshold,
                    redirect_rate_limit_quarantine_threshold = EXCLUDED.redirect_rate_limit_quarantine_threshold,
                    updated_at = EXCLUDED.updated_at,
                    updated_by_owner_id = EXCLUDED.updated_by_owner_id
                """,
                workspaceId,
                rawIpReviewEnabled,
                punycodeReviewEnabled,
                repeatedHostQuarantineThreshold,
                redirectRateLimitQuarantineThreshold,
                updatedAt,
                updatedByOwnerId);
        return findPolicy(workspaceId).orElseThrow();
    }

    @Override
    public List<WorkspaceHostRuleRecord> findHostRules(long workspaceId) {
        return jdbcTemplate.query(
                """
                SELECT id, workspace_id, host, rule_type, note, created_at, created_by_owner_id
                FROM workspace_host_rules
                WHERE workspace_id = ?
                ORDER BY created_at DESC, id DESC
                """,
                this::mapRule,
                workspaceId);
    }

    @Override
    public Optional<WorkspaceHostRuleRecord> findHostRule(long workspaceId, String host, String ruleType) {
        String sql = """
                SELECT id, workspace_id, host, rule_type, note, created_at, created_by_owner_id
                FROM workspace_host_rules
                WHERE workspace_id = ?
                  AND lower(host) = lower(?)
                """;
        if (ruleType != null) {
            sql += " AND rule_type = ?";
            return jdbcTemplate.query(sql, this::mapRule, workspaceId, host, ruleType).stream().findFirst();
        }
        return jdbcTemplate.query(sql, this::mapRule, workspaceId, host).stream().findFirst();
    }

    @Override
    public WorkspaceHostRuleRecord createHostRule(
            long workspaceId,
            String host,
            String ruleType,
            String note,
            OffsetDateTime createdAt,
            long createdByOwnerId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    """
                    INSERT INTO workspace_host_rules (workspace_id, host, rule_type, note, created_at, created_by_owner_id)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    new String[] {"id"});
            statement.setLong(1, workspaceId);
            statement.setString(2, host);
            statement.setString(3, ruleType);
            statement.setString(4, note);
            statement.setObject(5, createdAt);
            statement.setLong(6, createdByOwnerId);
            return statement;
        }, keyHolder);
        Long id = keyHolder.getKey() == null ? null : keyHolder.getKey().longValue();
        if (id == null) {
            throw new IllegalStateException("Workspace host rule insert failed");
        }
        return jdbcTemplate.query(
                        """
                        SELECT id, workspace_id, host, rule_type, note, created_at, created_by_owner_id
                        FROM workspace_host_rules
                        WHERE workspace_id = ? AND id = ?
                        """,
                        this::mapRule,
                        workspaceId,
                        id)
                .stream()
                .findFirst()
                .orElseThrow();
    }

    @Override
    public boolean deleteHostRule(long workspaceId, long ruleId) {
        return jdbcTemplate.update(
                "DELETE FROM workspace_host_rules WHERE workspace_id = ? AND id = ?",
                workspaceId,
                ruleId) == 1;
    }

    @Override
    public void incrementHostSignal(long workspaceId, String host, OffsetDateTime signaledAt) {
        jdbcTemplate.update(
                """
                INSERT INTO workspace_abuse_host_stats (workspace_id, host, signal_count, last_signaled_at)
                VALUES (?, ?, 1, ?)
                ON CONFLICT (workspace_id, host) DO UPDATE
                SET signal_count = workspace_abuse_host_stats.signal_count + 1,
                    last_signaled_at = EXCLUDED.last_signaled_at
                """,
                workspaceId,
                host,
                signaledAt);
    }

    @Override
    public long findHostSignalCount(long workspaceId, String host) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT signal_count
                FROM workspace_abuse_host_stats
                WHERE workspace_id = ?
                  AND host = ?
                """,
                Long.class,
                workspaceId,
                host);
        return count == null ? 0L : count;
    }

    @Override
    public List<WorkspaceAbuseTrendRecord> findTopFlaggedHosts(long workspaceId, OffsetDateTime since, int limit) {
        return jdbcTemplate.query(
                """
                SELECT target_host, COUNT(*) AS host_count, MAX(updated_at) AS latest_updated_at
                FROM link_abuse_cases
                WHERE workspace_id = ?
                  AND target_host IS NOT NULL
                  AND updated_at >= ?
                GROUP BY target_host
                ORDER BY host_count DESC, latest_updated_at DESC, target_host ASC
                LIMIT ?
                """,
                this::mapTrend,
                workspaceId,
                since,
                limit);
    }

    @Override
    public List<WorkspaceAbuseTrendRecord> findTopQuarantinedHosts(long workspaceId, OffsetDateTime since, int limit) {
        return jdbcTemplate.query(
                """
                SELECT target_host, COUNT(*) AS host_count, MAX(updated_at) AS latest_updated_at
                FROM link_abuse_cases
                WHERE workspace_id = ?
                  AND target_host IS NOT NULL
                  AND status = 'QUARANTINED'
                  AND updated_at >= ?
                GROUP BY target_host
                ORDER BY host_count DESC, latest_updated_at DESC, target_host ASC
                LIMIT ?
                """,
                this::mapTrend,
                workspaceId,
                since,
                limit);
    }

    @Override
    public Optional<OffsetDateTime> findLatestUpdatedAt(long workspaceId) {
        return jdbcTemplate.query(
                        """
                        SELECT MAX(latest_updated_at) AS latest_updated_at
                        FROM (
                            SELECT updated_at AS latest_updated_at FROM workspace_abuse_policies WHERE workspace_id = ?
                            UNION ALL
                            SELECT created_at AS latest_updated_at FROM workspace_host_rules WHERE workspace_id = ?
                            UNION ALL
                            SELECT last_signaled_at AS latest_updated_at FROM workspace_abuse_host_stats WHERE workspace_id = ?
                        ) timestamps
                        """,
                        (resultSet, rowNum) -> resultSet.getObject("latest_updated_at", OffsetDateTime.class),
                        workspaceId,
                        workspaceId,
                        workspaceId)
                .stream()
                .findFirst();
    }

    private WorkspaceAbusePolicyRecord mapPolicy(ResultSet resultSet, int rowNum) throws SQLException {
        return new WorkspaceAbusePolicyRecord(
                resultSet.getLong("workspace_id"),
                resultSet.getBoolean("raw_ip_review_enabled"),
                resultSet.getBoolean("punycode_review_enabled"),
                resultSet.getInt("repeated_host_quarantine_threshold"),
                resultSet.getInt("redirect_rate_limit_quarantine_threshold"),
                resultSet.getObject("updated_at", OffsetDateTime.class),
                resultSet.getObject("updated_by_owner_id", Long.class));
    }

    private WorkspaceHostRuleRecord mapRule(ResultSet resultSet, int rowNum) throws SQLException {
        return new WorkspaceHostRuleRecord(
                resultSet.getLong("id"),
                resultSet.getLong("workspace_id"),
                resultSet.getString("host"),
                resultSet.getString("rule_type"),
                resultSet.getString("note"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getLong("created_by_owner_id"));
    }

    private WorkspaceAbuseTrendRecord mapTrend(ResultSet resultSet, int rowNum) throws SQLException {
        return new WorkspaceAbuseTrendRecord(
                resultSet.getString("target_host"),
                resultSet.getLong("host_count"),
                resultSet.getObject("latest_updated_at", OffsetDateTime.class));
    }
}
