package com.linkplatform.api.runtime;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

@Component
public class LinkPlatformStartupValidator implements SmartInitializingSingleton {

    private final LinkPlatformQueryProperties queryProperties;
    private final LinkPlatformRuntimeProperties runtimeProperties;
    private final boolean cacheEnabled;

    public LinkPlatformStartupValidator(
            LinkPlatformQueryProperties queryProperties,
            LinkPlatformRuntimeProperties runtimeProperties,
            @org.springframework.beans.factory.annotation.Value("${link-platform.cache.enabled:true}") boolean cacheEnabled) {
        this.queryProperties = queryProperties;
        this.runtimeProperties = runtimeProperties;
        this.cacheEnabled = cacheEnabled;
    }

    @Override
    public void afterSingletonsInstantiated() {
        boolean partialQueryDatasourceConfig = queryProperties.getUrl() == null
                && (queryProperties.getUsername() != null
                        || (queryProperties.getPassword() != null && !queryProperties.getPassword().isBlank()));
        if (partialQueryDatasourceConfig) {
            throw new IllegalStateException(
                    "link-platform.query.datasource.url must be set when any dedicated query datasource property is configured");
        }

        boolean redirectEnabled = runtimeProperties.getMode() == RuntimeMode.ALL
                || runtimeProperties.getMode() == RuntimeMode.REDIRECT;
        String region = runtimeProperties.getRedirect().getRegion();
        String failoverRegion = runtimeProperties.getRedirect().getFailoverRegion();

        if (redirectEnabled && region == null) {
            throw new IllegalStateException(
                    "link-platform.runtime.redirect.region must be set when the redirect surface is enabled");
        }
        if (!redirectEnabled && failoverRegion != null) {
            throw new IllegalStateException(
                    "link-platform.runtime.redirect.failover-region requires a redirect-enabled runtime mode");
        }
        if (failoverRegion != null && failoverRegion.equalsIgnoreCase(region)) {
            throw new IllegalStateException(
                    "link-platform.runtime.redirect.failover-region must differ from link-platform.runtime.redirect.region");
        }
        if (failoverRegion != null && !cacheEnabled) {
            throw new IllegalStateException(
                    "link-platform.cache.enabled must remain true when link-platform.runtime.redirect.failover-region is configured");
        }
        boolean queryDatasourceDisallowed = queryProperties.isDedicatedConfigured()
                && (runtimeProperties.getMode() == RuntimeMode.REDIRECT || runtimeProperties.getMode() == RuntimeMode.WORKER);
        if (queryDatasourceDisallowed) {
            throw new IllegalStateException(
                    "Dedicated query datasource configuration is only valid for all or control-plane-api runtime modes");
        }
    }
}
