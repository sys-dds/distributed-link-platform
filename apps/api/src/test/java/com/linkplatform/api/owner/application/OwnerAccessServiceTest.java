package com.linkplatform.api.owner.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OwnerAccessServiceTest {

    private static final AuthenticatedOwner OWNER = new AuthenticatedOwner(1L, "free-owner", "Free Owner", OwnerPlan.FREE);
    private static final WorkspaceRecord PERSONAL_WORKSPACE = new WorkspaceRecord(
            101L,
            "free-owner",
            "Free Owner",
            true,
            OffsetDateTime.parse("2026-04-07T00:00:00Z"),
            OWNER.id(),
            null);

    private static final WorkspaceEntitlementService ALLOWING_ENTITLEMENTS = new WorkspaceEntitlementService(null, null, null, null) {
        @Override
        public boolean controlPlaneMutationsBlocked(long workspaceId) {
            return false;
        }
    };

    @Test
    void createdRevokedRotatedAndExpiredKeysBehavePredictably() {
        InMemoryOwnerApiKeyStore keyStore = new InMemoryOwnerApiKeyStore();
        InMemorySecurityEventStore securityEventStore = new InMemorySecurityEventStore();
        ApiKeyLifecycleService lifecycleService = new ApiKeyLifecycleService(keyStore, securityEventStore, new WorkspacePermissionService());
        OwnerAccessService accessService = new OwnerAccessService(
                new InMemoryOwnerStore(),
                new InMemoryWorkspaceStore(),
                new WorkspacePermissionService(),
                lifecycleService,
                new AllowAllRateLimitStore(),
                securityEventStore,
                ALLOWING_ENTITLEMENTS);

        WorkspaceAccessContext ownerContext = ownerContext();
        ApiKeyLifecycleService.CreatedApiKey created = lifecycleService.createKey(
                ownerContext,
                "primary",
                null,
                List.of("links:read"),
                OWNER.ownerKey());

        assertThat(accessService.authorizeRead(
                        created.plaintextKey(),
                        null,
                        null,
                        "GET",
                        "/api/v1/me",
                        "127.0.0.1",
                        ApiKeyScope.LINKS_READ)
                .ownerId()).isEqualTo(OWNER.id());

        lifecycleService.revoke(ownerContext, created.record().id(), OWNER.ownerKey());
        assertThatThrownBy(() -> accessService.authorizeRead(
                        created.plaintextKey(),
                        null,
                        null,
                        "GET",
                        "/api/v1/me",
                        "127.0.0.1",
                        ApiKeyScope.LINKS_READ))
                .isInstanceOf(ApiKeyAuthenticationException.class);

        ApiKeyLifecycleService.CreatedApiKey rotatedBase = lifecycleService.createKey(
                ownerContext,
                "rotate-me",
                null,
                List.of("links:read"),
                OWNER.ownerKey());
        ApiKeyLifecycleService.CreatedApiKey rotated = lifecycleService.rotate(
                ownerContext,
                rotatedBase.record().id(),
                null,
                List.of("links:read"),
                OWNER.ownerKey());
        assertThat(rotated.plaintextKey()).isNotEqualTo(rotatedBase.plaintextKey());
        assertThatThrownBy(() -> accessService.authorizeRead(
                        rotatedBase.plaintextKey(),
                        null,
                        null,
                        "GET",
                        "/api/v1/me",
                        "127.0.0.1",
                        ApiKeyScope.LINKS_READ))
                .isInstanceOf(ApiKeyAuthenticationException.class);
        assertThat(accessService.authorizeRead(
                        rotated.plaintextKey(),
                        null,
                        null,
                        "GET",
                        "/api/v1/me",
                        "127.0.0.1",
                        ApiKeyScope.LINKS_READ)
                .ownerId()).isEqualTo(OWNER.id());

        ApiKeyLifecycleService.CreatedApiKey expiring = lifecycleService.createKey(
                ownerContext,
                "expiring",
                OffsetDateTime.now().minusMinutes(1),
                List.of("links:read"),
                OWNER.ownerKey());
        assertThatThrownBy(() -> accessService.authorizeRead(
                        expiring.plaintextKey(),
                        null,
                        null,
                        "GET",
                        "/api/v1/me",
                        "127.0.0.1",
                        ApiKeyScope.LINKS_READ))
                .isInstanceOf(ApiKeyAuthenticationException.class);
    }

    @Test
    void malformedAmbiguousAndMissingCredentialsBehavePredictably() {
        InMemoryOwnerApiKeyStore keyStore = new InMemoryOwnerApiKeyStore();
        InMemorySecurityEventStore securityEventStore = new InMemorySecurityEventStore();
        ApiKeyLifecycleService lifecycleService = new ApiKeyLifecycleService(keyStore, securityEventStore, new WorkspacePermissionService());
        OwnerAccessService accessService = new OwnerAccessService(
                new InMemoryOwnerStore(),
                new InMemoryWorkspaceStore(),
                new WorkspacePermissionService(),
                lifecycleService,
                new AllowAllRateLimitStore(),
                securityEventStore,
                ALLOWING_ENTITLEMENTS);

        ApiKeyLifecycleService.CreatedApiKey created = lifecycleService.createKey(
                ownerContext(),
                "primary",
                null,
                List.of("links:read"),
                OWNER.ownerKey());

        assertThatThrownBy(() -> accessService.authorizeRead(
                        null,
                        null,
                        null,
                        "GET",
                        "/api/v1/me",
                        "127.0.0.1",
                        ApiKeyScope.LINKS_READ))
                .isInstanceOf(ApiKeyAuthenticationException.class)
                .hasMessageContaining("API credential is required");
        assertThatThrownBy(() -> accessService.authorizeRead(
                        null,
                        "Basic abc",
                        null,
                        "GET",
                        "/api/v1/me",
                        "127.0.0.1",
                        ApiKeyScope.LINKS_READ))
                .isInstanceOf(ApiKeyAuthenticationException.class)
                .hasMessageContaining("Bearer token");
        assertThatThrownBy(() -> accessService.authorizeRead(
                        created.plaintextKey(),
                        "Bearer something-else",
                        null,
                        "GET",
                        "/api/v1/me",
                        "127.0.0.1",
                        ApiKeyScope.LINKS_READ))
                .isInstanceOf(ApiKeyAuthenticationException.class)
                .hasMessageContaining("must match");
        assertThat(securityEventStore.types).contains(
                SecurityEventType.MISSING_CREDENTIAL,
                SecurityEventType.MALFORMED_BEARER,
                SecurityEventType.AMBIGUOUS_CREDENTIAL);
    }

    private WorkspaceAccessContext ownerContext() {
        return new WorkspaceAccessContext(
                OWNER,
                PERSONAL_WORKSPACE.id(),
                PERSONAL_WORKSPACE.slug(),
                PERSONAL_WORKSPACE.displayName(),
                true,
                WorkspaceRole.OWNER,
                WorkspaceRole.OWNER.impliedScopes(),
                null);
    }

    private static final class InMemoryOwnerStore implements OwnerStore {
        @Override
        public Optional<AuthenticatedOwner> findByApiKeyHash(String apiKeyHash) {
            return Optional.of(OWNER);
        }

        @Override
        public Optional<AuthenticatedOwner> findById(long ownerId) {
            return ownerId == OWNER.id() ? Optional.of(OWNER) : Optional.empty();
        }

        @Override
        public void lockById(long ownerId) {
        }
    }

    private static final class InMemoryWorkspaceStore implements WorkspaceStore {
        @Override
        public WorkspaceRecord createWorkspace(
                String slug,
                String displayName,
                boolean personalWorkspace,
                OffsetDateTime createdAt,
                long createdByOwnerId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<WorkspaceRecord> findBySlug(String slug) {
            return PERSONAL_WORKSPACE.slug().equals(slug) ? Optional.of(PERSONAL_WORKSPACE) : Optional.empty();
        }

        @Override
        public Optional<WorkspaceRecord> findById(long workspaceId) {
            return PERSONAL_WORKSPACE.id() == workspaceId ? Optional.of(PERSONAL_WORKSPACE) : Optional.empty();
        }

        @Override
        public Optional<WorkspaceRecord> findPersonalWorkspaceByOwnerId(long ownerId) {
            return ownerId == OWNER.id() ? Optional.of(PERSONAL_WORKSPACE) : Optional.empty();
        }

        @Override
        public List<WorkspaceRecord> findActiveWorkspacesForOwner(long ownerId) {
            return ownerId == OWNER.id() ? List.of(PERSONAL_WORKSPACE) : List.of();
        }

        @Override
        public Optional<WorkspaceMemberRecord> findActiveMembership(long workspaceId, long ownerId) {
            if (workspaceId == PERSONAL_WORKSPACE.id() && ownerId == OWNER.id()) {
                return Optional.of(new WorkspaceMemberRecord(
                        workspaceId,
                        ownerId,
                        WorkspaceRole.OWNER,
                        PERSONAL_WORKSPACE.createdAt(),
                        null,
                        null));
            }
            return Optional.empty();
        }

        @Override
        public Optional<WorkspaceMemberRecord> findActiveMembership(String workspaceSlug, long ownerId) {
            return findActiveMembership(PERSONAL_WORKSPACE.id(), ownerId)
                    .filter(member -> PERSONAL_WORKSPACE.slug().equals(workspaceSlug));
        }

        @Override
        public List<WorkspaceMemberRecord> findActiveMembers(long workspaceId) {
            return findActiveMembership(workspaceId, OWNER.id()).stream().toList();
        }

        @Override
        public Optional<WorkspaceMemberRecord> findMembership(long workspaceId, long ownerId) {
            return findActiveMembership(workspaceId, ownerId);
        }

        @Override
        public boolean addMember(long workspaceId, long ownerId, WorkspaceRole role, OffsetDateTime joinedAt, Long addedByOwnerId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addServiceAccountMember(long workspaceId, long ownerId, WorkspaceRole role, OffsetDateTime joinedAt, Long addedByOwnerId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean updateMemberRole(long workspaceId, long ownerId, WorkspaceRole role) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeMember(long workspaceId, long ownerId, OffsetDateTime removedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countActiveOwners(long workspaceId) {
            return 1L;
        }

        @Override
        public long countActiveHumanOwners(long workspaceId) {
            return 1L;
        }

        @Override
        public boolean suspendMember(long workspaceId, long ownerId, OffsetDateTime suspendedAt, Long suspendedByOwnerId, String suspendReason) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean resumeMember(long workspaceId, long ownerId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean suspendWorkspace(long workspaceId, OffsetDateTime suspendedAt, Long suspendedByOwnerId, String suspendReason) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean resumeWorkspace(long workspaceId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWorkspaceSuspended(long workspaceId) {
            return false;
        }
    }

    private static final class InMemoryOwnerApiKeyStore implements OwnerApiKeyStore {
        private final List<OwnerApiKeyRecord> records = new ArrayList<>();
        private long nextId = 1L;

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
            OwnerApiKeyRecord record = new OwnerApiKeyRecord(
                    nextId++,
                    ownerId,
                    OWNER.ownerKey(),
                    OWNER.plan(),
                    workspaceId,
                    PERSONAL_WORKSPACE.slug(),
                    keyPrefix,
                    keyHash,
                    label,
                    scopes,
                    createdAt,
                    null,
                    null,
                    expiresAt,
                    createdBy,
                    null);
            records.add(record);
            return record;
        }

        @Override
        public List<OwnerApiKeyRecord> findByWorkspaceId(long workspaceId) {
            return records.stream().filter(record -> record.workspaceId() == workspaceId).toList();
        }

        @Override
        public List<OwnerApiKeyRecord> findActiveByWorkspaceId(long workspaceId, OffsetDateTime now) {
            return records.stream()
                    .filter(record -> record.workspaceId() == workspaceId && record.activeAt(now))
                    .toList();
        }

        @Override
        public Optional<OwnerApiKeyRecord> findById(long workspaceId, long keyId) {
            return records.stream()
                    .filter(record -> record.workspaceId() == workspaceId && record.id() == keyId)
                    .findFirst();
        }

        @Override
        public Optional<OwnerApiKeyRecord> findActiveByHash(String keyHash, OffsetDateTime now) {
            return records.stream()
                    .filter(record -> record.keyHash().equals(keyHash) && record.activeAt(now))
                    .findFirst();
        }

        @Override
        public void revoke(long workspaceId, long keyId, OffsetDateTime revokedAt, String revokedBy) {
            replace(workspaceId, keyId, revokedAt, null, revokedBy, false);
        }

        @Override
        public void expire(long workspaceId, long keyId, OffsetDateTime expiresAt, String revokedBy) {
            replace(workspaceId, keyId, null, expiresAt, revokedBy, true);
        }

        @Override
        public void touchLastUsed(long keyId, OffsetDateTime lastUsedAt) {
            records.replaceAll(record -> record.id() == keyId
                    ? new OwnerApiKeyRecord(
                            record.id(),
                            record.ownerId(),
                            record.ownerKey(),
                            record.ownerPlan(),
                            record.workspaceId(),
                            record.workspaceSlug(),
                            record.keyPrefix(),
                            record.keyHash(),
                            record.label(),
                            record.scopes(),
                            record.createdAt(),
                            lastUsedAt,
                            record.revokedAt(),
                            record.expiresAt(),
                            record.createdBy(),
                            record.revokedBy())
                    : record);
        }

        @Override
        public void lockWorkspace(long workspaceId) {
        }

        private void replace(
                long workspaceId,
                long keyId,
                OffsetDateTime revokedAt,
                OffsetDateTime expiresAt,
                String actor,
                boolean expiryOnly) {
            records.replaceAll(record -> {
                if (record.workspaceId() != workspaceId || record.id() != keyId) {
                    return record;
                }
                return new OwnerApiKeyRecord(
                        record.id(),
                        record.ownerId(),
                        record.ownerKey(),
                        record.ownerPlan(),
                        record.workspaceId(),
                        record.workspaceSlug(),
                        record.keyPrefix(),
                        record.keyHash(),
                        record.label(),
                        record.scopes(),
                        record.createdAt(),
                        record.lastUsedAt(),
                        expiryOnly ? record.revokedAt() : revokedAt,
                        expiresAt == null ? record.expiresAt() : expiresAt,
                        record.createdBy(),
                        actor);
            });
        }
    }

    private static final class AllowAllRateLimitStore implements ControlPlaneRateLimitStore {
        @Override
        public boolean tryConsume(long ownerId, ControlPlaneRateLimitBucket bucket, OffsetDateTime windowStartedAt, int limit) {
            return true;
        }
    }

    private static final class InMemorySecurityEventStore implements SecurityEventStore {
        private final List<SecurityEventType> types = new ArrayList<>();

        @Override
        public void record(
                SecurityEventType eventType,
                Long ownerId,
                Long workspaceId,
                String apiKeyHash,
                String requestMethod,
                String requestPath,
                String remoteAddress,
                String detailSummary,
                OffsetDateTime occurredAt) {
            types.add(eventType);
        }
    }
}
