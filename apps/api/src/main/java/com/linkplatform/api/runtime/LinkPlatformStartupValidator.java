package com.linkplatform.api.runtime;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

@Component
public class LinkPlatformStartupValidator implements SmartInitializingSingleton {

    private final LinkPlatformQueryProperties queryProperties;

    public LinkPlatformStartupValidator(LinkPlatformQueryProperties queryProperties) {
        this.queryProperties = queryProperties;
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
    }
}
