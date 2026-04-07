package com.linkplatform.api.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "link-platform.runtime")
public class LinkPlatformRuntimeProperties {

    private RuntimeMode mode = RuntimeMode.ALL;
    private final Redirect redirect = new Redirect();
    private final Abuse abuse = new Abuse();

    public RuntimeMode getMode() {
        return mode;
    }

    public void setMode(RuntimeMode mode) {
        this.mode = mode == null ? RuntimeMode.ALL : mode;
    }

    public Redirect getRedirect() {
        return redirect;
    }

    public Abuse getAbuse() {
        return abuse;
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
        private final RateLimit rateLimit = new RateLimit();

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

        public RateLimit getRateLimit() {
            return rateLimit;
        }

        private String blankToNull(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }

    public static class RateLimit {

        private boolean enabled = false;
        private int requestsPerWindow = 10;
        private int windowSeconds = 60;
        private int hotSlugRequestsPerWindow = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getRequestsPerWindow() {
            return requestsPerWindow;
        }

        public void setRequestsPerWindow(int requestsPerWindow) {
            this.requestsPerWindow = requestsPerWindow;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }

        public int getHotSlugRequestsPerWindow() {
            return hotSlugRequestsPerWindow;
        }

        public void setHotSlugRequestsPerWindow(int hotSlugRequestsPerWindow) {
            this.hotSlugRequestsPerWindow = hotSlugRequestsPerWindow;
        }

        public int effectiveLimitForSlug(String slug) {
            if (slug == null || slug.isBlank()) {
                return requestsPerWindow;
            }
            return Math.min(requestsPerWindow, hotSlugRequestsPerWindow);
        }
    }

    public static class Abuse {

        private boolean enabled = true;
        private int autoQuarantineThreshold = 5;
        private int reviewPageSizeDefault = 20;
        private int reviewPageSizeMax = 100;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getAutoQuarantineThreshold() {
            return autoQuarantineThreshold;
        }

        public void setAutoQuarantineThreshold(int autoQuarantineThreshold) {
            this.autoQuarantineThreshold = autoQuarantineThreshold;
        }

        public int getReviewPageSizeDefault() {
            return reviewPageSizeDefault;
        }

        public void setReviewPageSizeDefault(int reviewPageSizeDefault) {
            this.reviewPageSizeDefault = reviewPageSizeDefault;
        }

        public int getReviewPageSizeMax() {
            return reviewPageSizeMax;
        }

        public void setReviewPageSizeMax(int reviewPageSizeMax) {
            this.reviewPageSizeMax = reviewPageSizeMax;
        }
    }

    @org.springframework.beans.factory.annotation.Value("${link-platform.abuse.enabled:true}")
    void bindAbuseEnabled(boolean enabled) {
        abuse.setEnabled(enabled);
    }

    @org.springframework.beans.factory.annotation.Value("${link-platform.abuse.auto-quarantine-threshold:5}")
    void bindAbuseAutoQuarantineThreshold(int threshold) {
        abuse.setAutoQuarantineThreshold(threshold);
    }

    @org.springframework.beans.factory.annotation.Value("${link-platform.abuse.review-page-size-default:20}")
    void bindAbuseReviewPageSizeDefault(int pageSize) {
        abuse.setReviewPageSizeDefault(pageSize);
    }

    @org.springframework.beans.factory.annotation.Value("${link-platform.abuse.review-page-size-max:100}")
    void bindAbuseReviewPageSizeMax(int pageSize) {
        abuse.setReviewPageSizeMax(pageSize);
    }
}
