package com.linkplatform.api.owner.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface WorkspaceStore {

    WorkspaceRecord createWorkspace(String slug, String displayName, boolean personalWorkspace, OffsetDateTime createdAt, long createdByOwnerId);

    Optional<WorkspaceRecord> findBySlug(String slug);

    Optional<WorkspaceRecord> findById(long workspaceId);

    Optional<WorkspaceRecord> findPersonalWorkspaceByOwnerId(long ownerId);

    List<WorkspaceRecord> findActiveWorkspacesForOwner(long ownerId);

    Optional<WorkspaceMemberRecord> findActiveMembership(long workspaceId, long ownerId);

    Optional<WorkspaceMemberRecord> findActiveMembership(String workspaceSlug, long ownerId);

    List<WorkspaceMemberRecord> findActiveMembers(long workspaceId);

    boolean addMember(long workspaceId, long ownerId, WorkspaceRole role, OffsetDateTime joinedAt, Long addedByOwnerId);

    boolean updateMemberRole(long workspaceId, long ownerId, WorkspaceRole role);

    boolean removeMember(long workspaceId, long ownerId, OffsetDateTime removedAt);

    long countActiveOwners(long workspaceId);
}
