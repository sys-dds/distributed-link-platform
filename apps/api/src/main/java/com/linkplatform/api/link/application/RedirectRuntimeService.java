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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnRuntimeModes({RuntimeMode.ALL, RuntimeMode.REDIRECT})
public class RedirectRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(RedirectRuntimeService.class);

    private final LinkStore linkStore;
    private final LinkReadCache linkReadCache;
    private final RedirectRateLimitService redirectRateLimitService;
    private final SecurityEventStore securityEventStore;
    private final LinkAbuseReviewService linkAbuseReviewService;
    private final LinkPlatformRuntimeProperties runtimeProperties;
    private final RedirectRuntimeState redirectRuntimeState;
    private final Clock clock;

    public RedirectRuntimeService(
            LinkStore linkStore,
            LinkReadCache linkReadCache,
            RedirectRateLimitService redirectRateLimitService,
            SecurityEventStore securityEventStore,
            LinkAbuseReviewService linkAbuseReviewService,
            LinkPlatformRuntimeProperties runtimeProperties,
            RedirectRuntimeState redirectRuntimeState,
            @Value("${link-platform.public-base-url}") String publicBaseUrl) {
        this.linkStore = linkStore;
        this.linkReadCache = linkReadCache;
        this.redirectRateLimitService = redirectRateLimitService;
        this.securityEventStore = securityEventStore;
        this.linkAbuseReviewService = linkAbuseReviewService;
        this.runtimeProperties = runtimeProperties;
        this.redirectRuntimeState = redirectRuntimeState;
        URI.create(publicBaseUrl);
        this.clock = Clock.systemUTC();
    }

    public RedirectDecision resolve(String slug, String requestPath, String queryString, String remoteAddress) {
        String validatedSlug = new LinkSlug(slug).value();
        RedirectRateLimitDecision rateLimitDecision = redirectRateLimitService.check(validatedSlug, requestPath, remoteAddress);
        if (!rateLimitDecision.allowed()) {
            recordSecurityEvent(
                    SecurityEventType.RATE_LIMIT_REJECTED,
                    requestPath,
                    remoteAddress,
                    "Redirect rate limit exceeded for slug " + validatedSlug,
                    OffsetDateTime.now(clock));
            throw new RedirectRateLimitExceededException("Public redirect rate limit exceeded");
        }
        LinkReadCache.PublicRedirectLookupResult cacheLookup = linkReadCache.lookupPublicRedirect(validatedSlug);
        return switch (cacheLookup.outcome()) {
            case GENERATION_UNAVAILABLE -> resolveAfterCacheGenerationUnavailable(
                    validatedSlug,
                    requestPath,
                    queryString,
                    remoteAddress);
            case DEGRADED -> resolveAfterCacheDegraded(
                    validatedSlug,
                    requestPath,
                    queryString,
                    remoteAddress);
            case HIT -> resolveFromCacheHit(cacheLookup.link().orElseThrow(), validatedSlug);
            case MISS -> resolveAfterCacheMiss(
                    validatedSlug,
                    cacheLookup.generation(),
                    requestPath,
                    queryString,
                    remoteAddress);
        };
    }

    private RedirectDecision resolveAfterCacheGenerationUnavailable(
            String slug,
            String requestPath,
            String queryString,
            String remoteAddress) {
        redirectRuntimeState.recordCacheBypass();
        return resolveFromPrimary(slug, LinkReadCache.CACHE_UNAVAILABLE_GENERATION, requestPath, queryString, remoteAddress, false);
    }

    private RedirectDecision resolveAfterCacheDegraded(
            String slug,
            String requestPath,
            String queryString,
            String remoteAddress) {
        redirectRuntimeState.recordCacheDegraded("redirect-cache-degraded");
        return resolveFromPrimary(slug, LinkReadCache.CACHE_UNAVAILABLE_GENERATION, requestPath, queryString, remoteAddress, false);
    }

    private RedirectDecision resolveFromCacheHit(Link link, String slug) {
        redirectRuntimeState.recordCacheHit();
        log.debug("redirect_runtime branch=cache-hit slug={}", slug);
        return new RedirectDecision(link.originalUrl().value(), true, false);
    }

    private RedirectDecision resolveAfterCacheMiss(
            String slug,
            long generation,
            String requestPath,
            String queryString,
            String remoteAddress) {
        redirectRuntimeState.recordCacheMiss();
        return resolveFromPrimary(slug, generation, requestPath, queryString, remoteAddress, true);
    }

    private RedirectDecision resolveFromPrimary(
            String slug,
            long generation,
            String requestPath,
            String queryString,
            String remoteAddress,
            boolean recordCacheMiss) {
        if (recordCacheMiss) {
            redirectRuntimeState.recordCacheMiss();
        }
        try {
            Link link = linkStore.findBySlug(slug, OffsetDateTime.now(clock))
                    .orElseThrow(() -> {
                        if (linkStore.findStoredDetailsBySlug(slug).map(LinkDetails::abuseStatus).orElse(LinkAbuseStatus.ACTIVE)
                                == LinkAbuseStatus.QUARANTINED) {
                            linkAbuseReviewService.recordQuarantinedRedirectAttempt(slug, requestPath, remoteAddress);
                            return new LinkQuarantinedException(slug);
                        }
                        return new LinkNotFoundException(slug);
                    });
            if (linkReadCache.isCacheGenerationAvailable(generation)) {
                linkReadCache.putPublicRedirect(slug, generation, link);
            }
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
        recordSecurityEvent(
                SecurityEventType.REDIRECT_LOOKUP_FAILED,
                requestPath,
                remoteAddress,
                "Redirect lookup failed in region " + runtimeProperties.getRedirect().getRegion() + ": " + reason,
                occurredAt);

        String failoverBaseUrl = runtimeProperties.getRedirect().getFailoverBaseUrl();
        String failoverRegion = runtimeProperties.getRedirect().getFailoverRegion();
        if (failoverBaseUrl != null && failoverRegion != null) {
            redirectRuntimeState.recordPrimaryFailureFailover(reason);
            redirectRuntimeState.recordFailoverActivated();
            recordSecurityEvent(
                    SecurityEventType.REDIRECT_FAILOVER_ACTIVATED,
                    requestPath,
                    remoteAddress,
                    "Redirect failover activated from " + runtimeProperties.getRedirect().getRegion()
                            + " to " + failoverRegion,
                    occurredAt);
            return new RedirectDecision(buildFailoverLocation(slug, queryString), false, true);
        }

        redirectRuntimeState.recordPrimaryFailureUnavailable(reason);
        redirectRuntimeState.recordUnavailable();
        recordSecurityEvent(
                SecurityEventType.REDIRECT_UNAVAILABLE,
                requestPath,
                remoteAddress,
                "Redirect unavailable in region " + runtimeProperties.getRedirect().getRegion() + ": " + reason,
                occurredAt);
        throw new RedirectLookupUnavailableException(
                "Redirect lookup temporarily unavailable for slug: " + slug,
                exception);
    }

    private void recordSecurityEvent(
            SecurityEventType eventType,
            String requestPath,
            String remoteAddress,
            String detailSummary,
            OffsetDateTime occurredAt) {
        try {
            securityEventStore.record(
                    eventType,
                    null,
                    null,
                    "GET",
                    requestPath,
                    remoteAddress,
                    detailSummary,
                    occurredAt);
        } catch (RuntimeException recordException) {
            log.warn(
                    "redirect_security_event_record_failed eventType={} reason={}",
                    eventType.name(),
                    recordException.getClass().getSimpleName());
        }
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
