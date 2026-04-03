package com.linkplatform.api.link.api;

import java.time.LocalDate;

public record DailyClickBucketResponse(LocalDate day, long clickTotal) {
}
