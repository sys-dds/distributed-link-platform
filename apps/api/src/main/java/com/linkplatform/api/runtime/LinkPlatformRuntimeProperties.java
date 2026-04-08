package com.linkplatform.api.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "link-platform.runtime")
public class LinkPlatformRuntimeProperties {

    private RuntimeMode mode = RuntimeMode.ALL;
    private final Redirect redirect = new Redirect();
    private final Abuse abuse = new Abuse();
    private final Webhooks webhooks = new Webhooks();
    private final Exports exports = new Exports();

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

    public Webhooks getWebhooks() {
        return webhooks;
    }

    public Exports getExports() {
        return exports;
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

    public static class Webhooks {

        private boolean enabled = true;
        private boolean allowPrivateCallbackHosts = false;
        private boolean allowHttpCallbacks = false;
        private long runnerDelayMs = 5_000L;
        private int deliveryBatchSize = 20;
        private int parkedThreshold = 5;
        private int disableThreshold = 10;
        private int connectTimeoutSeconds = 5;
        private int requestTimeoutSeconds = 10;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAllowPrivateCallbackHosts() {
            return allowPrivateCallbackHosts;
        }

        public void setAllowPrivateCallbackHosts(boolean allowPrivateCallbackHosts) {
            this.allowPrivateCallbackHosts = allowPrivateCallbackHosts;
        }

        public boolean isAllowHttpCallbacks() {
            return allowHttpCallbacks;
        }

        public void setAllowHttpCallbacks(boolean allowHttpCallbacks) {
            this.allowHttpCallbacks = allowHttpCallbacks;
        }

        public boolean requireHttpsCallbacks() {
            return !allowHttpCallbacks;
        }

        public boolean requirePublicCallbackHosts() {
            return !allowPrivateCallbackHosts;
        }

        public int getDeliveryBatchSize() {
            return deliveryBatchSize;
        }

        public void setDeliveryBatchSize(int deliveryBatchSize) {
            this.deliveryBatchSize = deliveryBatchSize;
        }

        public long getRunnerDelayMs() {
            return runnerDelayMs;
        }

        public void setRunnerDelayMs(long runnerDelayMs) {
            this.runnerDelayMs = runnerDelayMs;
        }

        public int getParkedThreshold() {
            return parkedThreshold;
        }

        public void setParkedThreshold(int parkedThreshold) {
            this.parkedThreshold = parkedThreshold;
        }

        public int getDisableThreshold() {
            return disableThreshold;
        }

        public void setDisableThreshold(int disableThreshold) {
            this.disableThreshold = disableThreshold;
        }

        public int getConnectTimeoutSeconds() {
            return connectTimeoutSeconds;
        }

        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
        }

        public int getRequestTimeoutSeconds() {
            return requestTimeoutSeconds;
        }

        public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
            this.requestTimeoutSeconds = requestTimeoutSeconds;
        }
    }

    public static class Exports {

        private boolean enabled = true;
        private long runnerDelayMs = 10_000L;
        private long maxPayloadBytes = 5_000_000L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getMaxPayloadBytes() {
            return maxPayloadBytes;
        }

        public void setMaxPayloadBytes(long maxPayloadBytes) {
            this.maxPayloadBytes = maxPayloadBytes;
        }

        public long getRunnerDelayMs() {
            return runnerDelayMs;
        }

        public void setRunnerDelayMs(long runnerDelayMs) {
            this.runnerDelayMs = runnerDelayMs;
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

    @org.springframework.beans.factory.annotation.Value("${link-platform.webhooks.enabled:true}")
    void bindWebhooksEnabled(boolean enabled) {
        webhooks.setEnabled(enabled);
    }

    @org.springframework.beans.factory.annotation.Value("${link-platform.webhooks.allow-private-callback-hosts:false}")
    void bindWebhookAllowPrivateCallbackHosts(boolean allowPrivateCallbackHosts) {
        webhooks.setAllowPrivateCallbackHosts(allowPrivateCallbackHosts);
    }

    @org.springframework.beans.factory.annotation.Value("${link-platform.webhooks.allow-http-callbacks:false}")
    void bindWebhookAllowHttpCallbacks(boolean allowHttpCallbacks) {
        webhooks.setAllowHttpCallbacks(allowHttpCallbacks);
    }

    @org.springframework.beans.factory.annotation.Value("${link-platform.webhooks.delivery-batch-size:20}")
    void bindWebhookDeliveryBatchSize(int batchSize) {
        webhooks.setDeliveryBatchSize(batchSize);
    }

    @org.springframework.beans.factory.annotation.Value("${link-platform.webhooks.runner-delay-ms:5000}")
    void bindWebhookRunnerDelay(long runnerDelayMs) {
        webhooks.setRunnerDelayMs(runnerDelayMs);
    }

    @org.springframework.beans.factory.annotation.Value("${link-platform.webhooks.parked-threshold:5}")
    void bindWebhookParkedThreshold(int parkedThreshold) {
        webhooks.setParkedThreshold(parkedThreshold);
    }

    @org.springframework.beans.factory.annotation.Value("${link-platform.webhooks.disable-threshold:10}")
    void bindWebhookDisableThreshold(int disableThreshold) {
        webhooks.setDisableThreshold(disableThreshold);
    }

    @org.springframework.beans.factory.annotation.Value("${link-platform.webhooks.connect-timeout-seconds:5}")
    void bindWebhookConnectTimeout(int timeoutSeconds) {
        webhooks.setConnectTimeoutSeconds(timeoutSeconds);
    }

    @org.springframework.beans.factory.annotation.Value("${link-platform.webhooks.request-timeout-seconds:10}")
    void bindWebhookRequestTimeout(int timeoutSeconds) {
        webhooks.setRequestTimeoutSeconds(timeoutSeconds);
    }

    @org.springframework.beans.factory.annotation.Value("${link-platform.exports.enabled:true}")
    void bindExportsEnabled(boolean enabled) {
        exports.setEnabled(enabled);
    }

    @org.springframework.beans.factory.annotation.Value("${link-platform.exports.runner-delay-ms:10000}")
    void bindExportsRunnerDelay(long runnerDelayMs) {
        exports.setRunnerDelayMs(runnerDelayMs);
    }

    @org.springframework.beans.factory.annotation.Value("${link-platform.exports.max-payload-bytes:5000000}")
    void bindExportsMaxPayloadBytes(long maxPayloadBytes) {
        exports.setMaxPayloadBytes(maxPayloadBytes);
    }
}
