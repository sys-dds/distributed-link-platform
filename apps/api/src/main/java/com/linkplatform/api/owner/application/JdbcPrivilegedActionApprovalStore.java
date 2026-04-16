package com.linkplatform.api.owner.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcPrivilegedActionApprovalStore implements PrivilegedActionApprovalStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPrivilegedActionApprovalStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PrivilegedActionApprovalRecord createApproved(
            long workspaceId,
            String actionType,
            long initiatorOwnerId,
            long approverOwnerId,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO privileged_action_approvals (
                    workspace_id,
                    action_type,
                    initiator_owner_id,
                    approver_owner_id,
                    status,
                    created_at,
                    expires_at
                ) VALUES (?, ?, ?, ?, 'APPROVED', ?, ?)
                RETURNING id,
                          workspace_id,
                          action_type,
                          initiator_owner_id,
                          approver_owner_id,
                          status,
                          created_at,
                          consumed_at,
                          expires_at
                """,
                (resultSet, rowNum) -> mapRecord(resultSet),
                workspaceId,
                actionType,
                initiatorOwnerId,
                approverOwnerId,
                createdAt,
                expiresAt);
    }

    @Override
    @Transactional
    public Optional<PrivilegedActionApprovalRecord> consumeApproved(
            long workspaceId,
            String actionType,
            long initiatorOwnerId,
            OffsetDateTime now) {
        expireApproved(now);
        return jdbcTemplate.query(
                        """
                        UPDATE privileged_action_approvals
                        SET status = 'CONSUMED',
                            consumed_at = ?
                        WHERE id = (
                            SELECT id
                            FROM privileged_action_approvals
                            WHERE workspace_id = ?
                              AND action_type = ?
                              AND initiator_owner_id = ?
                              AND status = 'APPROVED'
                              AND expires_at > ?
                            ORDER BY created_at ASC, id ASC
                            LIMIT 1
                            FOR UPDATE SKIP LOCKED
                        )
                        RETURNING id,
                                  workspace_id,
                                  action_type,
                                  initiator_owner_id,
                                  approver_owner_id,
                                  status,
                                  created_at,
                                  consumed_at,
                                  expires_at
                        """,
                        (resultSet, rowNum) -> mapRecord(resultSet),
                        now,
                        workspaceId,
                        actionType,
                        initiatorOwnerId,
                        now)
                .stream()
                .findFirst();
    }

    private void expireApproved(OffsetDateTime now) {
        jdbcTemplate.update(
                """
                UPDATE privileged_action_approvals
                SET status = 'EXPIRED'
                WHERE status = 'APPROVED'
                  AND expires_at <= ?
                """,
                now);
    }

    private PrivilegedActionApprovalRecord mapRecord(ResultSet resultSet) throws SQLException {
        return new PrivilegedActionApprovalRecord(
                resultSet.getLong("id"),
                resultSet.getLong("workspace_id"),
                resultSet.getString("action_type"),
                resultSet.getLong("initiator_owner_id"),
                resultSet.getLong("approver_owner_id"),
                resultSet.getString("status"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("consumed_at", OffsetDateTime.class),
                resultSet.getObject("expires_at", OffsetDateTime.class));
    }
}
