package com.linkplatform.api.owner.application;

public record OperatorActionLogQuery(
        String subsystem,
        int limit,
        String cursor) {
}
