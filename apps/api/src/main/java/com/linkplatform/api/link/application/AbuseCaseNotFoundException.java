package com.linkplatform.api.link.application;

public class AbuseCaseNotFoundException extends RuntimeException {

    public AbuseCaseNotFoundException(long caseId) {
        super("Abuse case not found: " + caseId);
    }
}
