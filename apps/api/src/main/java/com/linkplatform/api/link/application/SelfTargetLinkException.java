package com.linkplatform.api.link.application;

public class SelfTargetLinkException extends RuntimeException {

    public SelfTargetLinkException(String originalUrl) {
        super("Original URL cannot point to the Link Platform itself: " + originalUrl);
    }
}
