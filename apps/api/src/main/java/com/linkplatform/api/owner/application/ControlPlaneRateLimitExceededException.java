package com.linkplatform.api.owner.application;

public class ControlPlaneRateLimitExceededException extends RuntimeException {

    public ControlPlaneRateLimitExceededException(String message) {
        super(message);
    }
}
