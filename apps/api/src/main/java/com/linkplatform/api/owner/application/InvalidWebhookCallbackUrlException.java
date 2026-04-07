package com.linkplatform.api.owner.application;

public class InvalidWebhookCallbackUrlException extends RuntimeException {

    public InvalidWebhookCallbackUrlException(String message) {
        super(message);
    }
}
