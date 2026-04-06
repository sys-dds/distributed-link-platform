package com.linkplatform.api.link.api;

import java.time.OffsetDateTime;

public record ChangeLinkLifecycleRequest(String action, OffsetDateTime expiresAt) {
}
