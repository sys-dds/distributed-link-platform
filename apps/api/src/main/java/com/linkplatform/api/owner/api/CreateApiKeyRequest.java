package com.linkplatform.api.owner.api;

import java.time.OffsetDateTime;

public record CreateApiKeyRequest(String label, OffsetDateTime expiresAt) {
}
