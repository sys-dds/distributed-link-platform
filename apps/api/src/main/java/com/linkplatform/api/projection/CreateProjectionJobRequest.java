package com.linkplatform.api.projection;

public record CreateProjectionJobRequest(
        ProjectionJobType jobType,
        Long ownerId,
        String slug) {
}
