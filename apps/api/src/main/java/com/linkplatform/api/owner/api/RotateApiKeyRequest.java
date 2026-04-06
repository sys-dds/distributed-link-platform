package com.linkplatform.api.owner.api;

import java.time.OffsetDateTime;

public record RotateApiKeyRequest(OffsetDateTime expiresAt) {
}
