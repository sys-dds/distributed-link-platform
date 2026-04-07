package com.linkplatform.api.owner.api;

import java.time.OffsetDateTime;
import java.util.List;

public record RotateApiKeyRequest(OffsetDateTime expiresAt, List<String> scopes) {
}
