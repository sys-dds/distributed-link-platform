package com.linkplatform.api.owner.api;

public record CreatedApiKeyResponse(
        OwnerApiKeyResponse apiKey,
        String plaintextKey) {
}
