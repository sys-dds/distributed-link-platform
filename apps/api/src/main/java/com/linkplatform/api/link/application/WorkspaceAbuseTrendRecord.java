package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;

public record WorkspaceAbuseTrendRecord(
        String host,
        long count,
        OffsetDateTime latestUpdatedAt) {
}
