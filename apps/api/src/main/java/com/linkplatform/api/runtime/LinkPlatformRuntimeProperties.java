package com.linkplatform.api.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "link-platform.runtime")
public class LinkPlatformRuntimeProperties {

    private RuntimeMode mode = RuntimeMode.ALL;
    private final Redirect redirect = new Redirect();

    public RuntimeMode getMode() {
        return mode;
    }

    public void setMode(RuntimeMode mode) {
        this.mode = mode == null ? RuntimeMode.ALL : mode;
    }

    public Redirect getRedirect() {
        return redirect;
    }

    public boolean redirectEnabled() {
        return mode == RuntimeMode.ALL || mode == RuntimeMode.REDIRECT;
    }

    public boolean controlPlaneEnabled() {
        return mode == RuntimeMode.ALL || mode == RuntimeMode.CONTROL_PLANE_API;
    }

    public boolean workerEnabled() {
        return mode == RuntimeMode.ALL || mode == RuntimeMode.WORKER;
    }

    public boolean httpEnabled() {
        return mode.webServerEnabled();
    }

    public static class Redirect {

        private String region = "local";
        private String failoverRegion;
        private String failoverBaseUrl;

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = blankToNull(region);
        }

        public String getFailoverRegion() {
            return failoverRegion;
        }

        public void setFailoverRegion(String failoverRegion) {
            this.failoverRegion = blankToNull(failoverRegion);
        }

        public String getFailoverBaseUrl() {
            return failoverBaseUrl;
        }

        public void setFailoverBaseUrl(String failoverBaseUrl) {
            this.failoverBaseUrl = blankToNull(failoverBaseUrl);
        }

        public boolean failoverConfigured() {
            return failoverRegion != null && failoverBaseUrl != null;
        }

        private String blankToNull(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }
}
