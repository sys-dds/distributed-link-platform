package com.linkplatform.api.owner.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OwnerAccessServiceTest {

    private static final AuthenticatedOwner OWNER = new AuthenticatedOwner(1L, "free-owner", "Free Owner", OwnerPlan.FREE);

    @Test
    void createdRevokedRotatedAndExpiredKeysBehavePredictably() {
        InMemoryOwnerApiKeyStore keyStore = new InMemoryOwnerApiKeyStore();
        InMemorySecurityEventStore securityEventStore = new InMemorySecurityEventStore();
        ApiKeyLifecycleService lifecycleService = new ApiKeyLifecycleService(keyStore, securityEventStore);
        OwnerAccessService accessService = new OwnerAccessService(
                new InMemoryOwnerStore(keyStore),
                lifecycleService,
                new AllowAllRateLimitStore(),
                securityEventStore);

        ApiKeyLifecycleService.CreatedApiKey created = lifecycleService.createKey(OWNER, "primary", null, OWNER.ownerKey());
        assertThat(accessService.authorizeRead(created.plaintextKey(), null, "GET", "/api/v1/me", "127.0.0.1").id()).isEqualTo(1L);

        lifecycleService.revoke(OWNER, created.record().id(), OWNER.ownerKey());
        assertThatThrownBy(() -> accessService.authorizeRead(created.plaintextKey(), null, "GET", "/api/v1/me", "127.0.0.1"))
                .isInstanceOf(ApiKeyAuthenticationException.class);

        ApiKeyLifecycleService.CreatedApiKey rotatedBase = lifecycleService.createKey(OWNER, "rotate-me", null, OWNER.ownerKey());
        ApiKeyLifecycleService.CreatedApiKey rotated = lifecycleService.rotate(OWNER, rotatedBase.record().id(), null, OWNER.ownerKey());
        assertThat(rotated.plaintextKey()).isNotEqualTo(rotatedBase.plaintextKey());
        assertThatThrownBy(() -> accessService.authorizeRead(rotatedBase.plaintextKey(), null, "GET", "/api/v1/me", "127.0.0.1"))
                .isInstanceOf(ApiKeyAuthenticationException.class);
        assertThat(accessService.authorizeRead(rotated.plaintextKey(), null, "GET", "/api/v1/me", "127.0.0.1").id()).isEqualTo(1L);

        ApiKeyLifecycleService.CreatedApiKey expiring = lifecycleService.createKey(OWNER, "expiring", OffsetDateTime.now().minusMinutes(1), OWNER.ownerKey());
        assertThatThrownBy(() -> accessService.authorizeRead(expiring.plaintextKey(), null, "GET", "/api/v1/me", "127.0.0.1"))
                .isInstanceOf(ApiKeyAuthenticationException.class);
    }

    @Test
    void malformedAmbiguousAndMissingCredentialsBehavePredictably() {
        InMemoryOwnerApiKeyStore keyStore = new InMemoryOwnerApiKeyStore();
        InMemorySecurityEventStore securityEventStore = new InMemorySecurityEventStore();
        ApiKeyLifecycleService lifecycleService = new ApiKeyLifecycleService(keyStore, securityEventStore);
        OwnerAccessService accessService = new OwnerAccessService(
                new InMemoryOwnerStore(keyStore),
                lifecycleService,
                new AllowAllRateLimitStore(),
                securityEventStore);
        ApiKeyLifecycleService.CreatedApiKey created = lifecycleService.createKey(OWNER, "primary", null, OWNER.ownerKey());

        assertThatThrownBy(() -> accessService.authorizeRead(null, null, "GET", "/api/v1/me", "127.0.0.1"))
                .isInstanceOf(ApiKeyAuthenticationException.class)
                .hasMessageContaining("API credential is required");
        assertThatThrownBy(() -> accessService.authorizeRead(null, "Basic abc", "GET", "/api/v1/me", "127.0.0.1"))
                .isInstanceOf(ApiKeyAuthenticationException.class)
                .hasMessageContaining("Bearer token");
        assertThatThrownBy(() -> accessService.authorizeRead(created.plaintextKey(), "Bearer something-else", "GET", "/api/v1/me", "127.0.0.1"))
                .isInstanceOf(ApiKeyAuthenticationException.class)
                .hasMessageContaining("must match");
        assertThat(securityEventStore.types).contains(
                SecurityEventType.MISSING_CREDENTIAL,
                SecurityEventType.MALFORMED_BEARER,
                SecurityEventType.AMBIGUOUS_CREDENTIAL);
    }

    private static final class InMemoryOwnerStore implements OwnerStore {
        private final InMemoryOwnerApiKeyStore keyStore;
        private InMemoryOwnerStore(InMemoryOwnerApiKeyStore keyStore) { this.keyStore = keyStore; }
        @Override public Optional<AuthenticatedOwner> findByApiKeyHash(String apiKeyHash) { return keyStore.findActiveByHash(apiKeyHash, OffsetDateTime.now()).map(record -> OWNER); }
        @Override public void lockById(long ownerId) { }
    }

    private static final class InMemoryOwnerApiKeyStore implements OwnerApiKeyStore {
        private final List<OwnerApiKeyRecord> records = new ArrayList<>();
        private long nextId = 1L;
        @Override public OwnerApiKeyRecord create(long ownerId, String keyPrefix, String keyHash, String label, OffsetDateTime createdAt, OffsetDateTime expiresAt, String createdBy) {
            OwnerApiKeyRecord record = new OwnerApiKeyRecord(nextId++, ownerId, OWNER.ownerKey(), OWNER.plan(), keyPrefix, keyHash, label, createdAt, null, null, expiresAt, createdBy, null);
            records.add(record);
            return record;
        }
        @Override public List<OwnerApiKeyRecord> findByOwnerId(long ownerId) { return records.stream().filter(record -> record.ownerId() == ownerId).toList(); }
        @Override public List<OwnerApiKeyRecord> findActiveByOwnerId(long ownerId, OffsetDateTime now) { return records.stream().filter(record -> record.ownerId() == ownerId && record.activeAt(now)).toList(); }
        @Override public Optional<OwnerApiKeyRecord> findById(long ownerId, long keyId) { return records.stream().filter(record -> record.ownerId() == ownerId && record.id() == keyId).findFirst(); }
        @Override public Optional<OwnerApiKeyRecord> findActiveByHash(String keyHash, OffsetDateTime now) { return records.stream().filter(record -> record.keyHash().equals(keyHash) && record.activeAt(now)).findFirst(); }
        @Override public void revoke(long ownerId, long keyId, OffsetDateTime revokedAt, String revokedBy) { replace(ownerId, keyId, revokedAt, null, revokedBy, false); }
        @Override public void expire(long ownerId, long keyId, OffsetDateTime expiresAt, String revokedBy) { replace(ownerId, keyId, null, expiresAt, revokedBy, true); }
        @Override public void touchLastUsed(long keyId, OffsetDateTime lastUsedAt) { records.replaceAll(record -> record.id() == keyId ? new OwnerApiKeyRecord(record.id(), record.ownerId(), record.ownerKey(), record.ownerPlan(), record.keyPrefix(), record.keyHash(), record.label(), record.createdAt(), lastUsedAt, record.revokedAt(), record.expiresAt(), record.createdBy(), record.revokedBy()) : record); }
        @Override public void lockOwner(long ownerId) { }
        private void replace(long ownerId, long keyId, OffsetDateTime revokedAt, OffsetDateTime expiresAt, String actor, boolean expiryOnly) {
            records.replaceAll(record -> {
                if (record.ownerId() != ownerId || record.id() != keyId) {
                    return record;
                }
                return new OwnerApiKeyRecord(
                        record.id(),
                        record.ownerId(),
                        record.ownerKey(),
                        record.ownerPlan(),
                        record.keyPrefix(),
                        record.keyHash(),
                        record.label(),
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
        @Override public boolean tryConsume(long ownerId, ControlPlaneRateLimitBucket bucket, OffsetDateTime windowStartedAt, int limit) { return true; }
    }

    private static final class InMemorySecurityEventStore implements SecurityEventStore {
        private final List<SecurityEventType> types = new ArrayList<>();
        @Override public void record(SecurityEventType eventType, Long ownerId, String apiKeyHash, String requestMethod, String requestPath, String remoteAddress, String detailSummary, OffsetDateTime occurredAt) { types.add(eventType); }
    }
}
