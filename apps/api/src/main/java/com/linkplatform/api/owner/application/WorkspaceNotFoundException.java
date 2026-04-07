package com.linkplatform.api.owner.application;

public class WorkspaceNotFoundException extends RuntimeException {

    public WorkspaceNotFoundException(String message) {
        super(message);
    }
}
