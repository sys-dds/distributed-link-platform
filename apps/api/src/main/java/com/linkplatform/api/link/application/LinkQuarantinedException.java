package com.linkplatform.api.link.application;

public class LinkQuarantinedException extends LinkNotFoundException {

    public LinkQuarantinedException(String slug) {
        super(slug);
    }
}
