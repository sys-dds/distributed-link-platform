package com.linkplatform.api.link.application;

public class RedirectRateLimitExceededException extends RuntimeException {

    public RedirectRateLimitExceededException(String message) {
        super(message);
    }
}
