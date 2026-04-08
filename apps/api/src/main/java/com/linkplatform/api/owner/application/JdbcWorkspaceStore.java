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
public class JdbcWorkspaceStore implements WorkspaceStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkspaceStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public WorkspaceRecord createWorkspace(
            String slug,
            String displayName,
            boolean personalWorkspace,
            OffsetDateTime createdAt,
            long createdByOwnerId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    """
                    INSERT INTO workspaces (slug, display_name, personal_workspace, status, created_at, created_by_owner_id, archived_at)
                    VALUES (?, ?, ?, 'ACTIVE', ?, ?, NULL)
                    """,
                    new String[] {"id"});
            statement.setString(1, slug);
            statement.setString(2, displayName);
            statement.setBoolean(3, personalWorkspace);
            statement.setObject(4, createdAt);
            statement.setLong(5, createdByOwnerId);
            return statement;
        }, keyHolder);
        Number id = keyHolder.getKey();
        return findById(id.longValue()).orElseThrow();
    }

    @Override
    public Optional<WorkspaceRecord> findBySlug(String slug) {
        return jdbcTemplate.query(
                        """
                        SELECT w.id, w.slug, w.display_name, w.personal_workspace, w.created_at, w.created_by_owner_id, w.archived_at
                        FROM workspaces w
                        WHERE w.slug = ?
                        """,
                        (resultSet, rowNum) -> mapWorkspace(resultSet),
                        slug)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<WorkspaceRecord> findById(long workspaceId) {
        return jdbcTemplate.query(
                        """
                        SELECT w.id, w.slug, w.display_name, w.personal_workspace, w.created_at, w.created_by_owner_id, w.archived_at
                        FROM workspaces w
                        WHERE w.id = ?
                        """,
                        (resultSet, rowNum) -> mapWorkspace(resultSet),
                        workspaceId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<WorkspaceRecord> findPersonalWorkspaceByOwnerId(long ownerId) {
        return jdbcTemplate.query(
                        """
                        SELECT w.id, w.slug, w.display_name, w.personal_workspace, w.created_at, w.created_by_owner_id, w.archived_at
                        FROM workspaces w
                        WHERE w.personal_workspace = TRUE
                          AND w.created_by_owner_id = ?
                          AND w.archived_at IS NULL
                        """,
                        (resultSet, rowNum) -> mapWorkspace(resultSet),
                        ownerId)
                .stream()
                .findFirst();
    }

    @Override
    public List<WorkspaceRecord> findActiveWorkspacesForOwner(long ownerId) {
        return jdbcTemplate.query(
                """
                SELECT w.id, w.slug, w.display_name, w.personal_workspace, w.created_at, w.created_by_owner_id, w.archived_at,
                       wm.role AS caller_role
                FROM workspaces w
                JOIN workspace_members wm ON wm.workspace_id = w.id
                WHERE wm.owner_id = ?
                  AND wm.removed_at IS NULL
                  AND wm.suspended_at IS NULL
                  AND w.archived_at IS NULL
                ORDER BY w.personal_workspace DESC, w.created_at DESC, w.id DESC
                """,
                (resultSet, rowNum) -> mapWorkspace(resultSet),
                ownerId);
    }

    @Override
    public Optional<WorkspaceMemberRecord> findActiveMembership(long workspaceId, long ownerId) {
        return jdbcTemplate.query(
                        membershipSelect() + """
                                 WHERE wm.workspace_id = ?
                                   AND wm.owner_id = ?
                                   AND wm.removed_at IS NULL
                                   AND wm.suspended_at IS NULL
                                """,
                        (resultSet, rowNum) -> mapMember(resultSet),
                        workspaceId,
                        ownerId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<WorkspaceMemberRecord> findActiveMembership(String workspaceSlug, long ownerId) {
        return jdbcTemplate.query(
                        membershipSelect() + """
                                 JOIN workspaces w ON w.id = wm.workspace_id
                                 WHERE w.slug = ?
                                   AND wm.owner_id = ?
                                   AND wm.removed_at IS NULL
                                   AND wm.suspended_at IS NULL
                                   AND w.archived_at IS NULL
                                """,
                        (resultSet, rowNum) -> mapMember(resultSet),
                        workspaceSlug,
                        ownerId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<WorkspaceMemberRecord> findMembership(long workspaceId, long ownerId) {
        return jdbcTemplate.query(
                        membershipSelect() + """
                                 WHERE wm.workspace_id = ?
                                   AND wm.owner_id = ?
                                   AND wm.removed_at IS NULL
                                """,
                        (resultSet, rowNum) -> mapMember(resultSet),
                        workspaceId,
                        ownerId)
                .stream()
                .findFirst();
    }

    @Override
    public List<WorkspaceMemberRecord> findActiveMembers(long workspaceId) {
        return jdbcTemplate.query(
                membershipSelect() + """
                         WHERE wm.workspace_id = ?
                           AND wm.removed_at IS NULL
                           AND wm.suspended_at IS NULL
                         ORDER BY CASE wm.role WHEN 'OWNER' THEN 0 WHEN 'ADMIN' THEN 1 WHEN 'EDITOR' THEN 2 ELSE 3 END,
                                  o.id ASC
                        """,
                (resultSet, rowNum) -> mapMember(resultSet),
                workspaceId);
    }

    @Override
    public boolean addMember(long workspaceId, long ownerId, WorkspaceRole role, OffsetDateTime joinedAt, Long addedByOwnerId) {
        return upsertMember(workspaceId, ownerId, role, joinedAt, addedByOwnerId, "HUMAN");
    }

    @Override
    public boolean addServiceAccountMember(long workspaceId, long ownerId, WorkspaceRole role, OffsetDateTime joinedAt, Long addedByOwnerId) {
        return upsertMember(workspaceId, ownerId, role, joinedAt, addedByOwnerId, "SERVICE_ACCOUNT");
    }

    @Override
    public boolean updateMemberRole(long workspaceId, long ownerId, WorkspaceRole role) {
        return jdbcTemplate.update(
                """
                UPDATE workspace_members
                SET role = ?
                WHERE workspace_id = ?
                  AND owner_id = ?
                  AND removed_at IS NULL
                """,
                role.name(),
                workspaceId,
                ownerId) == 1;
    }

    @Override
    public boolean removeMember(long workspaceId, long ownerId, OffsetDateTime removedAt) {
        return jdbcTemplate.update(
                """
                UPDATE workspace_members
                SET removed_at = ?
                WHERE workspace_id = ?
                  AND owner_id = ?
                  AND removed_at IS NULL
                """,
                removedAt,
                workspaceId,
                ownerId) == 1;
    }

    @Override
    public long countActiveOwners(long workspaceId) {
        return countActiveHumanOwners(workspaceId);
    }

    @Override
    public long countActiveHumanOwners(long workspaceId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM workspace_members
                WHERE workspace_id = ?
                  AND role = 'OWNER'
                  AND member_type = 'HUMAN'
                  AND removed_at IS NULL
                  AND suspended_at IS NULL
                """,
                Long.class,
                workspaceId);
        return count == null ? 0L : count;
    }

    @Override
    public boolean suspendMember(
            long workspaceId,
            long ownerId,
            OffsetDateTime suspendedAt,
            Long suspendedByOwnerId,
            String suspendReason) {
        return jdbcTemplate.update(
                """
                UPDATE workspace_members
                SET suspended_at = ?,
                    suspended_by_owner_id = ?,
                    suspend_reason = ?
                WHERE workspace_id = ?
                  AND owner_id = ?
                  AND removed_at IS NULL
                  AND suspended_at IS NULL
                """,
                suspendedAt,
                suspendedByOwnerId,
                suspendReason,
                workspaceId,
                ownerId) == 1;
    }

    @Override
    public boolean resumeMember(long workspaceId, long ownerId) {
        return jdbcTemplate.update(
                """
                UPDATE workspace_members
                SET suspended_at = NULL,
                    suspended_by_owner_id = NULL,
                    suspend_reason = NULL
                WHERE workspace_id = ?
                  AND owner_id = ?
                  AND removed_at IS NULL
                  AND suspended_at IS NOT NULL
                """,
                workspaceId,
                ownerId) == 1;
    }

    @Override
    public boolean suspendWorkspace(long workspaceId, OffsetDateTime suspendedAt, Long suspendedByOwnerId, String suspendReason) {
        return jdbcTemplate.update(
                """
                UPDATE workspaces
                SET status = 'SUSPENDED',
                    suspended_at = ?,
                    suspended_by_owner_id = ?,
                    suspend_reason = ?
                WHERE id = ?
                  AND archived_at IS NULL
                  AND status <> 'SUSPENDED'
                """,
                suspendedAt,
                suspendedByOwnerId,
                suspendReason,
                workspaceId) == 1;
    }

    @Override
    public boolean resumeWorkspace(long workspaceId) {
        return jdbcTemplate.update(
                """
                UPDATE workspaces
                SET status = 'ACTIVE',
                    suspended_at = NULL,
                    suspended_by_owner_id = NULL,
                    suspend_reason = NULL
                WHERE id = ?
                  AND archived_at IS NULL
                  AND status = 'SUSPENDED'
                """,
                workspaceId) == 1;
    }

    @Override
    public boolean isWorkspaceSuspended(long workspaceId) {
        Boolean suspended = jdbcTemplate.queryForObject(
                """
                SELECT status = 'SUSPENDED'
                FROM workspaces
                WHERE id = ?
                """,
                Boolean.class,
                workspaceId);
        return Boolean.TRUE.equals(suspended);
    }

    private String membershipSelect() {
        return """
                SELECT wm.workspace_id,
                       wm.owner_id,
                       o.owner_key,
                       o.display_name,
                       wm.role,
                       wm.joined_at,
                       wm.added_by_owner_id,
                       wm.removed_at,
                       wm.member_type,
                       wm.suspended_at,
                       wm.suspended_by_owner_id,
                       wm.suspend_reason
                FROM workspace_members wm
                JOIN owners o ON o.id = wm.owner_id
                """;
    }

    private boolean upsertMember(
            long workspaceId,
            long ownerId,
            WorkspaceRole role,
            OffsetDateTime joinedAt,
            Long addedByOwnerId,
            String memberType) {
        return jdbcTemplate.update(
                """
                INSERT INTO workspace_members (
                    workspace_id, owner_id, role, joined_at, added_by_owner_id, removed_at, member_type, suspended_at, suspended_by_owner_id, suspend_reason
                )
                VALUES (?, ?, ?, ?, ?, NULL, ?, NULL, NULL, NULL)
                ON CONFLICT (workspace_id, owner_id) DO UPDATE
                SET role = EXCLUDED.role,
                    joined_at = EXCLUDED.joined_at,
                    added_by_owner_id = EXCLUDED.added_by_owner_id,
                    removed_at = NULL,
                    member_type = EXCLUDED.member_type,
                    suspended_at = NULL,
                    suspended_by_owner_id = NULL,
                    suspend_reason = NULL
                """,
                workspaceId,
                ownerId,
                role.name(),
                joinedAt,
                addedByOwnerId,
                memberType) == 1;
    }

    private WorkspaceRecord mapWorkspace(ResultSet resultSet) throws SQLException {
        String callerRole = null;
        try {
            callerRole = resultSet.getString("caller_role");
        } catch (SQLException ignored) {
            callerRole = null;
        }
        return new WorkspaceRecord(
                resultSet.getLong("id"),
                resultSet.getString("slug"),
                resultSet.getString("display_name"),
                resultSet.getBoolean("personal_workspace"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getLong("created_by_owner_id"),
                resultSet.getObject("archived_at", OffsetDateTime.class),
                callerRole == null ? null : WorkspaceRole.valueOf(callerRole));
    }

    private WorkspaceMemberRecord mapMember(ResultSet resultSet) throws SQLException {
        return new WorkspaceMemberRecord(
                resultSet.getLong("workspace_id"),
                resultSet.getLong("owner_id"),
                resultSet.getString("owner_key"),
                resultSet.getString("display_name"),
                WorkspaceRole.valueOf(resultSet.getString("role")),
                resultSet.getObject("joined_at", OffsetDateTime.class),
                getNullableLong(resultSet, "added_by_owner_id"),
                resultSet.getObject("removed_at", OffsetDateTime.class),
                resultSet.getString("member_type"),
                resultSet.getObject("suspended_at", OffsetDateTime.class),
                getNullableLong(resultSet, "suspended_by_owner_id"),
                resultSet.getString("suspend_reason"));
    }

    private Long getNullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }
}
