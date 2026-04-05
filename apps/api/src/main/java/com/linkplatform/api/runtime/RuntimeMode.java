package com.linkplatform.api.runtime;

public enum RuntimeMode {
    ALL,
    CONTROL_PLANE_API,
    REDIRECT,
    WORKER;

    public boolean webServerEnabled() {
        return this != WORKER;
    }
}
