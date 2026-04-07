package com.linkplatform.api.owner.api;

import java.time.OffsetDateTime;
import java.util.List;

public record CreateApiKeyRequest(String label, OffsetDateTime expiresAt, List<String> scopes) {
}
