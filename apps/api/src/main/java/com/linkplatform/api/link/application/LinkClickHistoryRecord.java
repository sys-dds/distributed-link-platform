package com.linkplatform.api.link.application;

import java.time.LocalDate;

public record LinkClickHistoryRecord(long clickId, String slug, LocalDate rollupDate) {
}
