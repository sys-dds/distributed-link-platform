package com.linkplatform.api.link.application;

import java.time.LocalDate;

public record DailyClickBucket(LocalDate day, long clickTotal) {
}
