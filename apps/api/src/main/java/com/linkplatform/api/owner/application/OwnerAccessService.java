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
    private final ApiKeyLifecycleService apiKeyLifecycleService;
    private final ControlPlaneRateLimitStore controlPlaneRateLimitStore;
    private final SecurityEventStore securityEventStore;
    private final Clock clock;

    public OwnerAccessService(
            OwnerStore ownerStore,
            ApiKeyLifecycleService apiKeyLifecycleService,
            ControlPlaneRateLimitStore controlPlaneRateLimitStore,
            SecurityEventStore securityEventStore) {
        this.ownerStore = ownerStore;
        this.apiKeyLifecycleService = apiKeyLifecycleService;
        this.controlPlaneRateLimitStore = controlPlaneRateLimitStore;
        this.securityEventStore = securityEventStore;
        this.clock = Clock.systemUTC();
    }

    public AuthenticatedOwner authorizeRead(String apiKey, String requestMethod, String requestPath, String remoteAddress) {
        return authorize(apiKey, null, ControlPlaneRateLimitBucket.READ, requestMethod, requestPath, remoteAddress);
    }

    public AuthenticatedOwner authorizeRead(
            String apiKey,
            String authorizationHeader,
            String requestMethod,
            String requestPath,
            String remoteAddress) {
        return authorize(apiKey, authorizationHeader, ControlPlaneRateLimitBucket.READ, requestMethod, requestPath, remoteAddress);
    }

    public AuthenticatedOwner authorizeMutation(String apiKey, String requestMethod, String requestPath, String remoteAddress) {
        return authorize(apiKey, null, ControlPlaneRateLimitBucket.MUTATION, requestMethod, requestPath, remoteAddress);
    }

    public AuthenticatedOwner authorizeMutation(
            String apiKey,
            String authorizationHeader,
            String requestMethod,
            String requestPath,
            String remoteAddress) {
        return authorize(apiKey, authorizationHeader, ControlPlaneRateLimitBucket.MUTATION, requestMethod, requestPath, remoteAddress);
    }

    private AuthenticatedOwner authorize(
            String apiKey,
            String authorizationHeader,
            ControlPlaneRateLimitBucket bucket,
            String requestMethod,
            String requestPath,
            String remoteAddress) {
        String resolvedApiKey = resolveApiKey(apiKey, authorizationHeader, requestMethod, requestPath, remoteAddress);
        if (resolvedApiKey == null) {
            securityEventStore.record(
                    SecurityEventType.MISSING_CREDENTIAL,
                    null,
                    null,
                    requestMethod,
                    requestPath,
                    remoteAddress,
                    "Missing API credential rejected",
                    OffsetDateTime.now(clock));
            throw new ApiKeyAuthenticationException(
                    "API credential is required via X-API-Key or Authorization: Bearer <token>");
        }

        String apiKeyHash = sha256(resolvedApiKey);
        OwnerApiKeyRecord apiKeyRecord = apiKeyLifecycleService.authenticate(resolvedApiKey);
        if (apiKeyRecord == null) {
            securityEventStore.record(
                    SecurityEventType.INVALID_CREDENTIAL,
                    null,
                    apiKeyHash,
                    requestMethod,
                    requestPath,
                    remoteAddress,
                    "Invalid API credential rejected",
                    OffsetDateTime.now(clock));
            throw new ApiKeyAuthenticationException("API credential is invalid");
        }
        AuthenticatedOwner owner = ownerStore.findByApiKeyHash(apiKeyHash)
                .orElseThrow(() -> new ApiKeyAuthenticationException("API credential is invalid"));

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
        apiKeyLifecycleService.markUsed(apiKeyRecord);
        return owner;
    }

    private String resolveApiKey(
            String apiKeyHeader,
            String authorizationHeader,
            String requestMethod,
            String requestPath,
            String remoteAddress) {
        String normalizedApiKey = trimToNull(apiKeyHeader);
        String bearerToken = extractBearerToken(authorizationHeader, requestMethod, requestPath, remoteAddress);
        if (normalizedApiKey != null && bearerToken != null && !normalizedApiKey.equals(bearerToken)) {
            securityEventStore.record(
                    SecurityEventType.AMBIGUOUS_CREDENTIAL,
                    null,
                    null,
                    requestMethod,
                    requestPath,
                    remoteAddress,
                    "Conflicting API credentials rejected",
                    OffsetDateTime.now(clock));
            throw new ApiKeyAuthenticationException(
                    "X-API-Key and Authorization credentials must match when both are provided");
        }
        return normalizedApiKey != null ? normalizedApiKey : bearerToken;
    }

    private String extractBearerToken(
            String authorizationHeader,
            String requestMethod,
            String requestPath,
            String remoteAddress) {
        String normalizedAuthorization = trimToNull(authorizationHeader);
        if (normalizedAuthorization == null) {
            return null;
        }
        if (!normalizedAuthorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            securityEventStore.record(
                    SecurityEventType.MALFORMED_BEARER,
                    null,
                    null,
                    requestMethod,
                    requestPath,
                    remoteAddress,
                    "Malformed Authorization header rejected",
                    OffsetDateTime.now(clock));
            throw new ApiKeyAuthenticationException("Authorization header must use Bearer token");
        }
        String bearerToken = trimToNull(normalizedAuthorization.substring(7));
        if (bearerToken == null) {
            securityEventStore.record(
                    SecurityEventType.MALFORMED_BEARER,
                    null,
                    null,
                    requestMethod,
                    requestPath,
                    remoteAddress,
                    "Blank bearer token rejected",
                    OffsetDateTime.now(clock));
            throw new ApiKeyAuthenticationException("Authorization bearer token is required");
        }
        return bearerToken;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
