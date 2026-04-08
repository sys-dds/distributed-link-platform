package com.linkplatform.api.owner.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyLifecycleService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OwnerApiKeyStore ownerApiKeyStore;
    private final SecurityEventStore securityEventStore;
    private final WorkspacePermissionService workspacePermissionService;
    private final WorkspaceEntitlementService workspaceEntitlementService;
    private final Clock clock;

    @Autowired
    public ApiKeyLifecycleService(
            OwnerApiKeyStore ownerApiKeyStore,
            SecurityEventStore securityEventStore,
            WorkspacePermissionService workspacePermissionService,
            WorkspaceEntitlementService workspaceEntitlementService) {
        this.ownerApiKeyStore = ownerApiKeyStore;
        this.securityEventStore = securityEventStore;
        this.workspacePermissionService = workspacePermissionService;
        this.workspaceEntitlementService = workspaceEntitlementService;
        this.clock = Clock.systemUTC();
    }

    public ApiKeyLifecycleService(
            OwnerApiKeyStore ownerApiKeyStore,
            SecurityEventStore securityEventStore,
            WorkspacePermissionService workspacePermissionService) {
        this(ownerApiKeyStore, securityEventStore, workspacePermissionService, null);
    }

    public ApiKeyLifecycleService(
            OwnerApiKeyStore ownerApiKeyStore,
            SecurityEventStore securityEventStore) {
        this(ownerApiKeyStore, securityEventStore, new WorkspacePermissionService(), null);
    }

    @Transactional
    public CreatedApiKey createKey(
            WorkspaceAccessContext context,
            String label,
            OffsetDateTime expiresAt,
            List<String> requestedScopes,
            String createdBy) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        ownerApiKeyStore.lockWorkspace(context.workspaceId());
        enforceActiveKeyLimit(context, now);
        GeneratedApiKey generatedApiKey = generateApiKey();
        Set<ApiKeyScope> scopes = workspacePermissionService.validateRequestedScopes(context.role(), requestedScopes);
        OwnerApiKeyRecord record = ownerApiKeyStore.create(
                context.ownerId(),
                context.workspaceId(),
                generatedApiKey.keyPrefix(),
                generatedApiKey.keyHash(),
                normalizeLabel(label),
                scopes,
                now,
                expiresAt,
                createdBy == null || createdBy.isBlank() ? context.ownerKey() : createdBy.trim());
        securityEventStore.record(
                SecurityEventType.API_KEY_CREATED,
                context.ownerId(),
                context.workspaceId(),
                generatedApiKey.keyHash(),
                "POST",
                "/api/v1/owner/api-keys",
                null,
                "Owner API key created",
                now);
        recordApiKeysSnapshot(context.workspaceId(), record.id(), now, "api_key_create");
        return new CreatedApiKey(record, generatedApiKey.plaintext());
    }

    public CreatedApiKey createKey(
            AuthenticatedOwner owner,
            String label,
            OffsetDateTime expiresAt,
            String createdBy) {
        return createKey(compatibilityContext(owner), label, expiresAt, WorkspaceRole.OWNER.impliedScopes().stream()
                .map(ApiKeyScope::value)
                .toList(), createdBy);
    }

    @Transactional(readOnly = true)
    public List<OwnerApiKeyRecord> listKeys(long workspaceId) {
        return ownerApiKeyStore.findByWorkspaceId(workspaceId);
    }

    @Transactional
    public void revoke(WorkspaceAccessContext context, long keyId, String revokedBy) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OwnerApiKeyRecord record = ownerApiKeyStore.findById(context.workspaceId(), keyId)
                .orElseThrow(() -> new ApiKeyAuthenticationException("Owner API key not found"));
        ownerApiKeyStore.revoke(context.workspaceId(), keyId, now, actor(context, revokedBy));
        securityEventStore.record(
                SecurityEventType.API_KEY_REVOKED,
                context.ownerId(),
                context.workspaceId(),
                record.keyHash(),
                "DELETE",
                "/api/v1/owner/api-keys/" + keyId,
                null,
                "Owner API key revoked",
                now);
    }

    @Transactional
    public CreatedApiKey rotate(
            WorkspaceAccessContext context,
            long keyId,
            OffsetDateTime expiresAt,
            List<String> requestedScopes,
            String rotatedBy) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        ownerApiKeyStore.lockWorkspace(context.workspaceId());
        OwnerApiKeyRecord existing = ownerApiKeyStore.findById(context.workspaceId(), keyId)
                .orElseThrow(() -> new ApiKeyAuthenticationException("Owner API key not found"));
        GeneratedApiKey generatedApiKey = generateApiKey();
        Set<ApiKeyScope> scopes = workspacePermissionService.validateRequestedScopes(context.role(), requestedScopes);
        enforceActiveKeyLimit(context, now);
        ownerApiKeyStore.expire(context.workspaceId(), keyId, now, actor(context, rotatedBy));
        OwnerApiKeyRecord created = ownerApiKeyStore.create(
                context.ownerId(),
                context.workspaceId(),
                generatedApiKey.keyPrefix(),
                generatedApiKey.keyHash(),
                existing.label(),
                scopes,
                now,
                expiresAt,
                actor(context, rotatedBy));
        securityEventStore.record(
                SecurityEventType.API_KEY_ROTATED,
                context.ownerId(),
                context.workspaceId(),
                generatedApiKey.keyHash(),
                "POST",
                "/api/v1/owner/api-keys/" + keyId + "/rotate",
                null,
                "Owner API key rotated",
                now);
        securityEventStore.record(
                SecurityEventType.API_KEY_EXPIRED,
                context.ownerId(),
                context.workspaceId(),
                existing.keyHash(),
                "POST",
                "/api/v1/owner/api-keys/" + keyId + "/rotate",
                null,
                "Owner API key expired by rotation",
                now);
        recordApiKeysSnapshot(context.workspaceId(), created.id(), now, "api_key_rotate");
        return new CreatedApiKey(created, generatedApiKey.plaintext());
    }

    public CreatedApiKey rotate(
            AuthenticatedOwner owner,
            long keyId,
            OffsetDateTime expiresAt,
            String rotatedBy) {
        return rotate(compatibilityContext(owner), keyId, expiresAt, WorkspaceRole.OWNER.impliedScopes().stream()
                .map(ApiKeyScope::value)
                .toList(), rotatedBy);
    }

    @Transactional(readOnly = true)
    public OwnerApiKeyRecord authenticate(String plaintextKey) {
        return ownerApiKeyStore.findActiveByHash(sha256(plaintextKey), OffsetDateTime.now(clock))
                .orElse(null);
    }

    @Transactional
    public void markUsed(OwnerApiKeyRecord record) {
        ownerApiKeyStore.touchLastUsed(record.id(), OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
    }

    private void enforceActiveKeyLimit(WorkspaceAccessContext context, OffsetDateTime now) {
        if (workspaceEntitlementService != null) {
            workspaceEntitlementService.enforceApiKeysQuota(context.workspaceId(), now);
            return;
        }
        int activeKeyLimit = context.plan() == OwnerPlan.FREE ? 2 : 10;
        long currentUsage = ownerApiKeyStore.findActiveByWorkspaceId(context.workspaceId(), now).size();
        if (currentUsage >= activeKeyLimit) {
            throw new WorkspaceQuotaExceededException(
                    WorkspaceUsageMetric.API_KEYS,
                    currentUsage,
                    activeKeyLimit,
                    "Workspace API key quota exceeded");
        }
    }

    private void recordApiKeysSnapshot(long workspaceId, long recordId, OffsetDateTime now, String source) {
        if (workspaceEntitlementService == null) {
            return;
        }
        workspaceEntitlementService.recordCurrentApiKeysSnapshot(workspaceId, source, Long.toString(recordId), now);
    }

    private String normalizeLabel(String label) {
        if (label == null || label.isBlank()) {
            return "default";
        }
        String normalized = label.trim();
        return normalized.length() <= 100 ? normalized : normalized.substring(0, 100);
    }

    private String actor(WorkspaceAccessContext context, String value) {
        return value == null || value.isBlank() ? context.ownerKey() : value.trim();
    }

    private WorkspaceAccessContext compatibilityContext(AuthenticatedOwner owner) {
        return new WorkspaceAccessContext(
                owner,
                owner.id(),
                owner.ownerKey(),
                owner.displayName(),
                true,
                WorkspaceRole.OWNER,
                WorkspaceRole.OWNER.impliedScopes(),
                null);
    }

    private GeneratedApiKey generateApiKey() {
        byte[] secretBytes = new byte[24];
        SECURE_RANDOM.nextBytes(secretBytes);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        String plaintext = "lkp_" + encoded;
        return new GeneratedApiKey(plaintext.substring(0, Math.min(12, plaintext.length())), sha256(plaintext), plaintext);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte part : hash) {
                hex.append(String.format("%02x", part));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    public record CreatedApiKey(OwnerApiKeyRecord record, String plaintextKey) {
    }

    private record GeneratedApiKey(String keyPrefix, String keyHash, String plaintext) {
    }
}
