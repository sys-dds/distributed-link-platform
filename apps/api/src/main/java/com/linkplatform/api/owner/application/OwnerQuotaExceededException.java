package com.linkplatform.api.owner.application;

public class OwnerQuotaExceededException extends RuntimeException {

    public OwnerQuotaExceededException(String message) {
        super(message);
    }
}
