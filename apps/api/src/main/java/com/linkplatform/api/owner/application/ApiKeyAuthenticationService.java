package com.linkplatform.api.owner.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Service;

@Service
public class ApiKeyAuthenticationService {

    private final OwnerStore ownerStore;

    public ApiKeyAuthenticationService(OwnerStore ownerStore) {
        this.ownerStore = ownerStore;
    }

    public AuthenticatedOwner authenticate(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiKeyAuthenticationException("X-API-Key header is required");
        }
        return ownerStore.findByApiKeyHash(sha256(apiKey.trim()))
                .orElseThrow(() -> new ApiKeyAuthenticationException("X-API-Key is invalid"));
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
