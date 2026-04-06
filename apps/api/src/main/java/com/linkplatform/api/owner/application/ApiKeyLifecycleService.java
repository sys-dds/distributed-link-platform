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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyLifecycleService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OwnerApiKeyStore ownerApiKeyStore;
    private final SecurityEventStore securityEventStore;
    private final Clock clock;

    public ApiKeyLifecycleService(
            OwnerApiKeyStore ownerApiKeyStore,
            SecurityEventStore securityEventStore) {
        this.ownerApiKeyStore = ownerApiKeyStore;
        this.securityEventStore = securityEventStore;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public CreatedApiKey createKey(AuthenticatedOwner owner, String label, OffsetDateTime expiresAt, String createdBy) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        ownerApiKeyStore.lockOwner(owner.id());
        enforceActiveKeyLimit(owner, now);
        GeneratedApiKey generatedApiKey = generateApiKey();
        OwnerApiKeyRecord record = ownerApiKeyStore.create(
                owner.id(),
                generatedApiKey.keyPrefix(),
                generatedApiKey.keyHash(),
                normalizeLabel(label),
                now,
                expiresAt,
                createdBy == null || createdBy.isBlank() ? owner.ownerKey() : createdBy.trim());
        securityEventStore.record(
                SecurityEventType.API_KEY_CREATED,
                owner.id(),
                generatedApiKey.keyHash(),
                "POST",
                "/api/v1/owner/api-keys",
                null,
                "Owner API key created",
                now);
        return new CreatedApiKey(record, generatedApiKey.plaintext());
    }

    @Transactional(readOnly = true)
    public List<OwnerApiKeyRecord> listKeys(long ownerId) {
        return ownerApiKeyStore.findByOwnerId(ownerId);
    }

    @Transactional
    public void revoke(AuthenticatedOwner owner, long keyId, String revokedBy) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OwnerApiKeyRecord record = ownerApiKeyStore.findById(owner.id(), keyId)
                .orElseThrow(() -> new ApiKeyAuthenticationException("Owner API key not found"));
        ownerApiKeyStore.revoke(owner.id(), keyId, now, actor(owner, revokedBy));
        securityEventStore.record(
                SecurityEventType.API_KEY_REVOKED,
                owner.id(),
                record.keyHash(),
                "DELETE",
                "/api/v1/owner/api-keys/" + keyId,
                null,
                "Owner API key revoked",
                now);
    }

    @Transactional
    public CreatedApiKey rotate(AuthenticatedOwner owner, long keyId, OffsetDateTime expiresAt, String rotatedBy) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OwnerApiKeyRecord existing = ownerApiKeyStore.findById(owner.id(), keyId)
                .orElseThrow(() -> new ApiKeyAuthenticationException("Owner API key not found"));
        GeneratedApiKey generatedApiKey = generateApiKey();
        ownerApiKeyStore.expire(owner.id(), keyId, now, actor(owner, rotatedBy));
        OwnerApiKeyRecord created = ownerApiKeyStore.create(
                owner.id(),
                generatedApiKey.keyPrefix(),
                generatedApiKey.keyHash(),
                existing.label(),
                now,
                expiresAt,
                actor(owner, rotatedBy));
        securityEventStore.record(
                SecurityEventType.API_KEY_ROTATED,
                owner.id(),
                generatedApiKey.keyHash(),
                "POST",
                "/api/v1/owner/api-keys/" + keyId + "/rotate",
                null,
                "Owner API key rotated",
                now);
        securityEventStore.record(
                SecurityEventType.API_KEY_EXPIRED,
                owner.id(),
                existing.keyHash(),
                "POST",
                "/api/v1/owner/api-keys/" + keyId + "/rotate",
                null,
                "Owner API key expired by rotation",
                now);
        return new CreatedApiKey(created, generatedApiKey.plaintext());
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

    private void enforceActiveKeyLimit(AuthenticatedOwner owner, OffsetDateTime now) {
        int activeKeyLimit = owner.plan() == OwnerPlan.FREE ? 2 : 10;
        if (ownerApiKeyStore.findActiveByOwnerId(owner.id(), now).size() >= activeKeyLimit) {
            throw new IllegalArgumentException("Active API key limit exceeded for owner " + owner.ownerKey());
        }
    }

    private String normalizeLabel(String label) {
        if (label == null || label.isBlank()) {
            return "default";
        }
        String normalized = label.trim();
        return normalized.length() <= 100 ? normalized : normalized.substring(0, 100);
    }

    private String actor(AuthenticatedOwner owner, String value) {
        return value == null || value.isBlank() ? owner.ownerKey() : value.trim();
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
