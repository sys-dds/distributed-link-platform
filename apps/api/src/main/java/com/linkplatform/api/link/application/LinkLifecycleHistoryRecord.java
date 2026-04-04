package com.linkplatform.api.link.application;

public record LinkLifecycleHistoryRecord(long outboxId, LinkLifecycleEvent event) {
}
