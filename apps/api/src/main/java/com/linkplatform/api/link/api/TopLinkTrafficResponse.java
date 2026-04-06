package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.TopLinkTraffic;

public record TopLinkTrafficResponse(String slug, String originalUrl, long clickTotal) {

    public static TopLinkTrafficResponse from(TopLinkTraffic topLinkTraffic) {
        return new TopLinkTrafficResponse(
                topLinkTraffic.slug(),
                topLinkTraffic.originalUrl(),
                topLinkTraffic.clickTotal());
    }
}
