package com.linkplatform.api.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "link-platform.query.datasource")
public class LinkPlatformQueryProperties {

    private String url;
    private String username;
    private String password;
    private String driverClassName;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = blankToNull(url);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = blankToNull(username);
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = blankToNull(driverClassName);
    }

    public boolean isDedicatedConfigured() {
        return url != null;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
