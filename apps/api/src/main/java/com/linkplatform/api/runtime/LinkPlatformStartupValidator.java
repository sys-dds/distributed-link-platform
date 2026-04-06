package com.linkplatform.api.runtime;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

@Component
public class LinkPlatformStartupValidator implements SmartInitializingSingleton {

    private final LinkPlatformQueryProperties queryProperties;
    private final LinkPlatformRuntimeProperties runtimeProperties;
    private final boolean cacheEnabled;
    private final String publicBaseUrl;

    public LinkPlatformStartupValidator(
            LinkPlatformQueryProperties queryProperties,
            LinkPlatformRuntimeProperties runtimeProperties,
            @org.springframework.beans.factory.annotation.Value("${link-platform.cache.enabled:true}") boolean cacheEnabled,
            @org.springframework.beans.factory.annotation.Value("${link-platform.public-base-url}") String publicBaseUrl) {
        this.queryProperties = queryProperties;
        this.runtimeProperties = runtimeProperties;
        this.cacheEnabled = cacheEnabled;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Override
    public void afterSingletonsInstantiated() {
        boolean partialQueryDatasourceConfig = queryProperties.getUrl() == null
                && (queryProperties.getUsername() != null
                        || queryProperties.getPassword() != null
                        || queryProperties.getDriverClassName() != null);
        if (partialQueryDatasourceConfig) {
            throw new IllegalStateException(
                    "link-platform.query.datasource.url must be set when any dedicated query datasource property is configured");
        }
        if (queryProperties.getUrl() != null && queryProperties.getUsername() == null) {
            throw new IllegalStateException(
                    "link-platform.query.datasource.username must be set when link-platform.query.datasource.url is configured");
        }

        boolean redirectEnabled = runtimeProperties.redirectEnabled();
        String region = runtimeProperties.getRedirect().getRegion();
        String failoverRegion = runtimeProperties.getRedirect().getFailoverRegion();
        String failoverBaseUrl = runtimeProperties.getRedirect().getFailoverBaseUrl();

        if (redirectEnabled && region == null) {
            throw new IllegalStateException(
                    "link-platform.runtime.redirect.region must be set when the redirect surface is enabled");
        }
        if (redirectEnabled && !cacheEnabled) {
            throw new IllegalStateException(
                    "link-platform.cache.enabled must remain true when the redirect surface is enabled");
        }
        if (!redirectEnabled && (failoverRegion != null || failoverBaseUrl != null)) {
            throw new IllegalStateException(
                    "Redirect failover configuration requires a redirect-enabled runtime mode");
        }
        if (failoverRegion != null && failoverRegion.equalsIgnoreCase(region)) {
            throw new IllegalStateException(
                    "link-platform.runtime.redirect.failover-region must differ from link-platform.runtime.redirect.region");
        }
        if ((failoverRegion == null) != (failoverBaseUrl == null)) {
            throw new IllegalStateException(
                    "link-platform.runtime.redirect.failover-region and link-platform.runtime.redirect.failover-base-url must be configured together");
        }
        if (redirectEnabled) {
            validateAbsoluteHttpUrl(publicBaseUrl, "link-platform.public-base-url");
        }
        if (failoverBaseUrl != null) {
            validateAbsoluteHttpUrl(failoverBaseUrl, "link-platform.runtime.redirect.failover-base-url");
            if (sameNormalizedUrl(publicBaseUrl, failoverBaseUrl)) {
                throw new IllegalStateException(
                        "link-platform.runtime.redirect.failover-base-url must differ from link-platform.public-base-url");
            }
        }
        boolean queryDatasourceDisallowed = queryProperties.isDedicatedConfigured()
                && (runtimeProperties.getMode() == RuntimeMode.REDIRECT || runtimeProperties.getMode() == RuntimeMode.WORKER);
        if (queryDatasourceDisallowed) {
            throw new IllegalStateException(
                    "Dedicated query datasource configuration is only valid for all or control-plane-api runtime modes");
        }
        if (!runtimeProperties.controlPlaneEnabled() && runtimeProperties.getMode() == RuntimeMode.WORKER && runtimeProperties.httpEnabled()) {
            throw new IllegalStateException("Worker-only runtime must not expose HTTP surfaces");
        }
    }

    private void validateAbsoluteHttpUrl(String value, String propertyName) {
        try {
            java.net.URI uri = java.net.URI.create(value);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalStateException(propertyName + " must be an absolute http/https URL");
            }
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalStateException(propertyName + " must be an absolute http/https URL");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(propertyName + " must be an absolute http/https URL", exception);
        }
    }

    private boolean sameNormalizedUrl(String left, String right) {
        java.net.URI leftUri = java.net.URI.create(left);
        java.net.URI rightUri = java.net.URI.create(right);
        return leftUri.getScheme().equalsIgnoreCase(rightUri.getScheme())
                && leftUri.getHost().equalsIgnoreCase(rightUri.getHost())
                && effectivePort(leftUri) == effectivePort(rightUri)
                && normalizePath(leftUri).equals(normalizePath(rightUri));
    }

    private int effectivePort(java.net.URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private String normalizePath(java.net.URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.endsWith("/") ? path : path + "/";
    }
}
