package com.linkplatform.api.link.application;

public enum LinkTrafficWindow {
    LAST_24_HOURS("24h"),
    LAST_7_DAYS("7d");

    private final String value;

    LinkTrafficWindow(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
