package com.linkplatform.api.owner.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;

@Service
public class OwnerAccessService {

    private final OwnerStore ownerStore;
    private final ControlPlaneRateLimitStore controlPlaneRateLimitStore;
    private final SecurityEventStore securityEventStore;
    private final Clock clock;

    public OwnerAccessService(
            OwnerStore ownerStore,
            ControlPlaneRateLimitStore controlPlaneRateLimitStore,
            SecurityEventStore securityEventStore) {
        this.ownerStore = ownerStore;
        this.controlPlaneRateLimitStore = controlPlaneRateLimitStore;
        this.securityEventStore = securityEventStore;
        this.clock = Clock.systemUTC();
    }

    public AuthenticatedOwner authorizeRead(String apiKey, String requestMethod, String requestPath, String remoteAddress) {
        return authorize(apiKey, ControlPlaneRateLimitBucket.READ, requestMethod, requestPath, remoteAddress);
    }

    public AuthenticatedOwner authorizeMutation(String apiKey, String requestMethod, String requestPath, String remoteAddress) {
        return authorize(apiKey, ControlPlaneRateLimitBucket.MUTATION, requestMethod, requestPath, remoteAddress);
    }

    private AuthenticatedOwner authorize(
            String apiKey,
            ControlPlaneRateLimitBucket bucket,
            String requestMethod,
            String requestPath,
            String remoteAddress) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiKeyAuthenticationException("X-API-Key header is required");
        }

        String apiKeyHash = sha256(apiKey.trim());
        AuthenticatedOwner owner = ownerStore.findByApiKeyHash(apiKeyHash).orElse(null);
        if (owner == null) {
            securityEventStore.record(
                    SecurityEventType.INVALID_API_KEY,
                    null,
                    apiKeyHash,
                    requestMethod,
                    requestPath,
                    remoteAddress,
                    "Invalid API key rejected",
                    OffsetDateTime.now(clock));
            throw new ApiKeyAuthenticationException("X-API-Key is invalid");
        }

        OffsetDateTime windowStartedAt = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        int limit = bucket == ControlPlaneRateLimitBucket.READ
                ? owner.plan().readRequestsPerMinute()
                : owner.plan().mutationRequestsPerMinute();
        if (!controlPlaneRateLimitStore.tryConsume(owner.id(), bucket, windowStartedAt, limit)) {
            securityEventStore.record(
                    SecurityEventType.RATE_LIMIT_REJECTED,
                    owner.id(),
                    apiKeyHash,
                    requestMethod,
                    requestPath,
                    remoteAddress,
                    "Rate limit bucket " + bucket.name() + " exceeded",
                    OffsetDateTime.now(clock));
            throw new ControlPlaneRateLimitExceededException(
                    "Control-plane " + bucket.name().toLowerCase() + " rate limit exceeded");
        }
        return owner;
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
}
