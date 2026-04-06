package com.linkplatform.api.link.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ClickRollupDriftRecord(
        Long ownerId,
        String slug,
        LocalDate bucketDay,
        long rawClickCount,
        long rollupClickCount,
        long driftCount,
        OffsetDateTime detectedAt,
        OffsetDateTime repairedAt,
        ClickRollupRepairStatus repairStatus,
        String repairNote) {
}
