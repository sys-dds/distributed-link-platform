package com.linkplatform.api.link.application;

import com.linkplatform.api.link.domain.Link;
import com.linkplatform.api.link.domain.LinkSlug;
import com.linkplatform.api.owner.application.SecurityEventStore;
import com.linkplatform.api.owner.application.SecurityEventType;
import com.linkplatform.api.runtime.ConditionalOnRuntimeModes;
import com.linkplatform.api.runtime.LinkPlatformRuntimeProperties;
import com.linkplatform.api.runtime.RedirectRuntimeState;
import com.linkplatform.api.runtime.RuntimeMode;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnRuntimeModes({RuntimeMode.ALL, RuntimeMode.REDIRECT})
public class RedirectRuntimeService {

    private final LinkStore linkStore;
    private final LinkReadCache linkReadCache;
    private final SecurityEventStore securityEventStore;
    private final LinkPlatformRuntimeProperties runtimeProperties;
    private final RedirectRuntimeState redirectRuntimeState;
    private final Clock clock;

    public RedirectRuntimeService(
            LinkStore linkStore,
            LinkReadCache linkReadCache,
            SecurityEventStore securityEventStore,
            LinkPlatformRuntimeProperties runtimeProperties,
            RedirectRuntimeState redirectRuntimeState,
            @Value("${link-platform.public-base-url}") String publicBaseUrl) {
        this.linkStore = linkStore;
        this.linkReadCache = linkReadCache;
        this.securityEventStore = securityEventStore;
        this.runtimeProperties = runtimeProperties;
        this.redirectRuntimeState = redirectRuntimeState;
        URI.create(publicBaseUrl);
        this.clock = Clock.systemUTC();
    }

    public RedirectDecision resolve(String slug, String requestPath, String queryString, String remoteAddress) {
        String validatedSlug = new LinkSlug(slug).value();
        long generation = linkReadCache.getPublicRedirectGeneration(validatedSlug);
        return linkReadCache.getPublicRedirect(validatedSlug, generation)
                .map(link -> {
                    redirectRuntimeState.recordCacheHit();
                    return new RedirectDecision(link.originalUrl().value(), true, false);
                })
                .orElseGet(() -> resolveFromPrimary(validatedSlug, generation, requestPath, queryString, remoteAddress));
    }

    private RedirectDecision resolveFromPrimary(
            String slug,
            long generation,
            String requestPath,
            String queryString,
            String remoteAddress) {
        redirectRuntimeState.recordCacheMiss();
        try {
            Link link = linkStore.findBySlug(slug, OffsetDateTime.now(clock))
                    .orElseThrow(() -> new LinkNotFoundException(slug));
            linkReadCache.putPublicRedirect(slug, generation, link);
            redirectRuntimeState.recordPrimaryLookupSuccess();
            return new RedirectDecision(link.originalUrl().value(), true, false);
        } catch (DataAccessException exception) {
            return handlePrimaryLookupFailure(slug, requestPath, queryString, remoteAddress, exception);
        }
    }

    private RedirectDecision handlePrimaryLookupFailure(
            String slug,
            String requestPath,
            String queryString,
            String remoteAddress,
            DataAccessException exception) {
        String reason = compactReason(exception);
        OffsetDateTime occurredAt = OffsetDateTime.now(clock);
        redirectRuntimeState.recordPrimaryLookupFailure(reason);
        securityEventStore.record(
                SecurityEventType.REDIRECT_LOOKUP_FAILED,
                null,
                null,
                "GET",
                requestPath,
                remoteAddress,
                "Redirect lookup failed in region " + runtimeProperties.getRedirect().getRegion() + ": " + reason,
                occurredAt);

        String failoverBaseUrl = runtimeProperties.getRedirect().getFailoverBaseUrl();
        String failoverRegion = runtimeProperties.getRedirect().getFailoverRegion();
        if (failoverBaseUrl != null && failoverRegion != null) {
            redirectRuntimeState.recordFailoverActivated();
            securityEventStore.record(
                    SecurityEventType.REDIRECT_FAILOVER_ACTIVATED,
                    null,
                    null,
                    "GET",
                    requestPath,
                    remoteAddress,
                    "Redirect failover activated from " + runtimeProperties.getRedirect().getRegion()
                            + " to " + failoverRegion,
                    occurredAt);
            return new RedirectDecision(buildFailoverLocation(slug, queryString), false, true);
        }

        redirectRuntimeState.recordUnavailable();
        securityEventStore.record(
                SecurityEventType.REDIRECT_UNAVAILABLE,
                null,
                null,
                "GET",
                requestPath,
                remoteAddress,
                "Redirect unavailable in region " + runtimeProperties.getRedirect().getRegion() + ": " + reason,
                occurredAt);
        throw new RedirectLookupUnavailableException(
                "Redirect lookup temporarily unavailable for slug: " + slug,
                exception);
    }

    private String buildFailoverLocation(String slug, String queryString) {
        String normalizedBase = runtimeProperties.getRedirect().getFailoverBaseUrl();
        if (!normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase + "/";
        }
        String relativePath = slug + (queryString == null || queryString.isBlank() ? "" : "?" + queryString);
        return URI.create(normalizedBase).resolve(relativePath).toString();
    }

    private String compactReason(Throwable throwable) {
        String message = throwable.getMessage();
        String summary = throwable.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return summary.length() <= 255 ? summary : summary.substring(0, 255);
    }
}
