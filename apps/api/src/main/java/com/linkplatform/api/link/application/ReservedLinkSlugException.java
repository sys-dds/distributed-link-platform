package com.linkplatform.api.link.application;

public class ReservedLinkSlugException extends RuntimeException {

    public ReservedLinkSlugException(String slug) {
        super("Link slug is reserved and cannot be used: " + slug);
    }
}
