package com.linkplatform.api.link.application;

public class DuplicateLinkSlugException extends RuntimeException {

    public DuplicateLinkSlugException(String slug) {
        super("Link slug already exists: " + slug);
    }
}
