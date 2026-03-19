package com.linkplatform.api.link.application;

public class LinkNotFoundException extends RuntimeException {

    public LinkNotFoundException(String slug) {
        super("Link slug not found: " + slug);
    }
}
