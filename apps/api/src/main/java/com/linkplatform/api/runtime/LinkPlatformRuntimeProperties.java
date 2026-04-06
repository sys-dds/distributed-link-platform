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

    public static class Redirect {

        private String region = "local";
        private String failoverRegion;

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

        private String blankToNull(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }
}
