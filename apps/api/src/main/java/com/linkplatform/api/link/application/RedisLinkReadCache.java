package com.linkplatform.api.link.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkplatform.api.link.domain.Link;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "link-platform.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisLinkReadCache implements LinkReadCache {

    private static final Logger log = LoggerFactory.getLogger(RedisLinkReadCache.class);
    private static final TypeReference<List<LinkDetails>> LINK_DETAILS_LIST = new TypeReference<>() {};
    private static final TypeReference<List<LinkSuggestion>> LINK_SUGGESTION_LIST = new TypeReference<>() {};
    private static final TypeReference<LinkDiscoveryPage> DISCOVERY_PAGE = new TypeReference<>() {};
    private static final TypeReference<List<LinkActivityEvent>> ACTIVITY_LIST = new TypeReference<>() {};
    private static final TypeReference<List<TopLinkTraffic>> TOP_LINK_LIST = new TypeReference<>() {};
    private static final TypeReference<List<TrendingLink>> TRENDING_LIST = new TypeReference<>() {};

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Duration redirectTtl;
    private final Duration ownerReadTtl;
    private final Duration analyticsTtl;

    public RedisLinkReadCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @org.springframework.beans.factory.annotation.Value("${link-platform.cache.redirect-ttl:PT5M}") Duration redirectTtl,
            @org.springframework.beans.factory.annotation.Value("${link-platform.cache.owner-read-ttl:PT30S}") Duration ownerReadTtl,
            @org.springframework.beans.factory.annotation.Value("${link-platform.cache.analytics-ttl:PT15S}") Duration analyticsTtl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.redirectTtl = redirectTtl;
        this.ownerReadTtl = ownerReadTtl;
        this.analyticsTtl = analyticsTtl;
    }

    @Override
    public long getPublicRedirectGeneration(String slug) {
        return getGeneration(redirectGenerationKey(slug));
    }

    @Override
    public Optional<Link> getPublicRedirect(String slug, long generation) {
        if (!isCacheGenerationAvailable(generation)) {
            return Optional.empty();
        }
        return readValue("redirect", redirectKey(slug, generation), Link.class);
    }

    @Override
    public void putPublicRedirect(String slug, long generation, Link link) {
        if (!isCacheGenerationAvailable(generation)) {
            return;
        }
        writeValue("redirect", redirectKey(slug, generation), link, redirectTtl);
    }

    @Override
    public void invalidatePublicRedirect(String slug) {
        incrementGeneration("redirect_invalidation", redirectGenerationKey(slug));
    }

    @Override
    public long getOwnerControlPlaneGeneration(long ownerId) {
        return getGeneration(ownerControlPlaneGenerationKey(ownerId));
    }

    @Override
    public Optional<LinkDetails> getOwnerLinkDetails(long ownerId, long generation, String slug) {
        if (!isCacheGenerationAvailable(generation)) {
            return Optional.empty();
        }
        return readValue("detail", ownerControlPlaneKey(ownerId, generation, "detail", slug), LinkDetails.class);
    }

    @Override
    public void putOwnerLinkDetails(long ownerId, long generation, String slug, LinkDetails linkDetails) {
        if (!isCacheGenerationAvailable(generation)) {
            return;
        }
        writeValue("detail", ownerControlPlaneKey(ownerId, generation, "detail", slug), linkDetails, ownerReadTtl);
    }

    @Override
    public Optional<List<LinkDetails>> getOwnerRecentLinks(long ownerId, long generation, int limit, String query, LinkLifecycleState state) {
        if (!isCacheGenerationAvailable(generation)) {
            return Optional.empty();
        }
        return readValue(
                "list",
                ownerControlPlaneKey(ownerId, generation, "list", sha256(limit + "|" + state.name() + "|" + normalize(query))),
                LINK_DETAILS_LIST);
    }

    @Override
    public void putOwnerRecentLinks(long ownerId, long generation, int limit, String query, LinkLifecycleState state, List<LinkDetails> linkDetails) {
        if (!isCacheGenerationAvailable(generation)) {
            return;
        }
        writeValue(
                "list",
                ownerControlPlaneKey(ownerId, generation, "list", sha256(limit + "|" + state.name() + "|" + normalize(query))),
                linkDetails,
                ownerReadTtl);
    }

    @Override
    public Optional<List<LinkSuggestion>> getOwnerSuggestions(long ownerId, long generation, String query, int limit) {
        if (!isCacheGenerationAvailable(generation)) {
            return Optional.empty();
        }
        return readValue(
                "suggestions",
                ownerControlPlaneKey(ownerId, generation, "suggestions", sha256(limit + "|" + normalize(query))),
                LINK_SUGGESTION_LIST);
    }

    @Override
    public void putOwnerSuggestions(long ownerId, long generation, String query, int limit, List<LinkSuggestion> suggestions) {
        if (!isCacheGenerationAvailable(generation)) {
            return;
        }
        writeValue(
                "suggestions",
                ownerControlPlaneKey(ownerId, generation, "suggestions", sha256(limit + "|" + normalize(query))),
                suggestions,
                ownerReadTtl);
    }

    @Override
    public Optional<LinkDiscoveryPage> getOwnerDiscoveryPage(long ownerId, long generation, LinkDiscoveryQuery query) {
        if (!isCacheGenerationAvailable(generation)) {
            return Optional.empty();
        }
        return readValue("discovery", ownerControlPlaneKey(ownerId, generation, "discovery", discoveryHash(query)), DISCOVERY_PAGE);
    }

    @Override
    public void putOwnerDiscoveryPage(long ownerId, long generation, LinkDiscoveryQuery query, LinkDiscoveryPage page) {
        if (!isCacheGenerationAvailable(generation)) {
            return;
        }
        writeValue("discovery", ownerControlPlaneKey(ownerId, generation, "discovery", discoveryHash(query)), page, ownerReadTtl);
    }

    @Override
    public long getOwnerAnalyticsGeneration(long ownerId) {
        return getGeneration(ownerAnalyticsGenerationKey(ownerId));
    }

    @Override
    public Optional<List<LinkActivityEvent>> getOwnerRecentActivity(long ownerId, long generation, int limit) {
        if (!isCacheGenerationAvailable(generation)) {
            return Optional.empty();
        }
        return readValue("activity", ownerAnalyticsKey(ownerId, generation, "activity", String.valueOf(limit)), ACTIVITY_LIST);
    }

    @Override
    public void putOwnerRecentActivity(long ownerId, long generation, int limit, List<LinkActivityEvent> activityEvents) {
        if (!isCacheGenerationAvailable(generation)) {
            return;
        }
        writeValue("activity", ownerAnalyticsKey(ownerId, generation, "activity", String.valueOf(limit)), activityEvents, analyticsTtl);
    }

    @Override
    public Optional<LinkTrafficSummary> getOwnerTrafficSummary(long ownerId, long generation, String slug) {
        if (!isCacheGenerationAvailable(generation)) {
            return Optional.empty();
        }
        return readValue("traffic_summary", ownerAnalyticsKey(ownerId, generation, "traffic_summary", slug), LinkTrafficSummary.class);
    }

    @Override
    public void putOwnerTrafficSummary(long ownerId, long generation, String slug, LinkTrafficSummary summary) {
        if (!isCacheGenerationAvailable(generation)) {
            return;
        }
        writeValue("traffic_summary", ownerAnalyticsKey(ownerId, generation, "traffic_summary", slug), summary, analyticsTtl);
    }

    @Override
    public Optional<List<TopLinkTraffic>> getOwnerTopLinks(long ownerId, long generation, LinkTrafficWindow window) {
        if (!isCacheGenerationAvailable(generation)) {
            return Optional.empty();
        }
        return readValue("top_links", ownerAnalyticsKey(ownerId, generation, "top_links", window.name()), TOP_LINK_LIST);
    }

    @Override
    public void putOwnerTopLinks(long ownerId, long generation, LinkTrafficWindow window, List<TopLinkTraffic> topLinks) {
        if (!isCacheGenerationAvailable(generation)) {
            return;
        }
        writeValue("top_links", ownerAnalyticsKey(ownerId, generation, "top_links", window.name()), topLinks, analyticsTtl);
    }

    @Override
    public Optional<List<TrendingLink>> getOwnerTrendingLinks(long ownerId, long generation, LinkTrafficWindow window, int limit) {
        if (!isCacheGenerationAvailable(generation)) {
            return Optional.empty();
        }
        return readValue("trending", ownerAnalyticsKey(ownerId, generation, "trending", window.name() + "|" + limit), TRENDING_LIST);
    }

    @Override
    public void putOwnerTrendingLinks(long ownerId, long generation, LinkTrafficWindow window, int limit, List<TrendingLink> trendingLinks) {
        if (!isCacheGenerationAvailable(generation)) {
            return;
        }
        writeValue("trending", ownerAnalyticsKey(ownerId, generation, "trending", window.name() + "|" + limit), trendingLinks, analyticsTtl);
    }

    @Override
    public void invalidateOwnerControlPlane(long ownerId) {
        incrementGeneration("control_plane_invalidation", ownerControlPlaneGenerationKey(ownerId));
    }

    @Override
    public void invalidateOwnerAnalytics(long ownerId) {
        incrementGeneration("analytics_invalidation", ownerAnalyticsGenerationKey(ownerId));
    }

    private <T> Optional<T> readValue(String area, String key, Class<T> type) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                meterRegistry.counter("link.cache.miss", "area", area).increment();
                return Optional.empty();
            }
            T parsed = objectMapper.readValue(value, type);
            meterRegistry.counter("link.cache.hit", "area", area).increment();
            return Optional.of(parsed);
        } catch (Exception exception) {
            handleReadFailure(area, key, exception);
            return Optional.empty();
        }
    }

    private <T> Optional<T> readValue(String area, String key, TypeReference<T> typeReference) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                meterRegistry.counter("link.cache.miss", "area", area).increment();
                return Optional.empty();
            }
            T parsed = objectMapper.readValue(value, typeReference);
            meterRegistry.counter("link.cache.hit", "area", area).increment();
            return Optional.of(parsed);
        } catch (Exception exception) {
            handleReadFailure(area, key, exception);
            return Optional.empty();
        }
    }

    private void writeValue(String area, String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception exception) {
            handleFailure("write", area, exception);
        }
    }

    private void delete(String area, String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception exception) {
            handleFailure("invalidate", area, exception);
        }
    }

    private void incrementGeneration(String area, String generationKey) {
        try {
            redisTemplate.opsForValue().increment(generationKey);
        } catch (Exception exception) {
            handleFailure("invalidate", area, exception);
        }
    }

    private String ownerControlPlaneKey(long ownerId, long generation, String area, String suffix) {
        return "link:owner:" + ownerId + ":cp:v" + generation + ":" + area + ":" + suffix;
    }

    private String ownerAnalyticsKey(long ownerId, long generation, String area, String suffix) {
        return "link:owner:" + ownerId + ":analytics:v" + generation + ":" + area + ":" + suffix;
    }

    private String ownerControlPlaneGenerationKey(long ownerId) {
        return "link:owner:" + ownerId + ":cp:gen";
    }

    private String ownerAnalyticsGenerationKey(long ownerId) {
        return "link:owner:" + ownerId + ":analytics:gen";
    }

    private String redirectKey(String slug, long generation) {
        return "link:redirect:" + slug + ":v" + generation;
    }

    private String redirectGenerationKey(String slug) {
        return "link:redirect:" + slug + ":gen";
    }

    private long getGeneration(String generationKey) {
        try {
            String stored = redisTemplate.opsForValue().get(generationKey);
            if (stored == null) {
                return 0L;
            }
            try {
                return Long.parseLong(stored);
            } catch (NumberFormatException invalidGeneration) {
                // Corrupted generation data should not permanently force cache bypass.
                delete("generation", generationKey);
                meterRegistry.counter("link.cache.miss", "area", "generation").increment();
                return 0L;
            }
        } catch (Exception exception) {
            handleFailure("read_generation", "generation", exception);
            return CACHE_UNAVAILABLE_GENERATION;
        }
    }

    private void handleReadFailure(String area, String key, Exception exception) {
        handleFailure("read", area, exception);
        delete(area, key);
    }

    private void handleFailure(String operation, String area, Exception exception) {
        meterRegistry.counter("link.cache.degraded", "area", area, "operation", operation).increment();
        log.warn("link_cache_{} area={} reason={}", operation, area, exception.getClass().getSimpleName());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte part : hash) {
                hex.append(String.format("%02x", part));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private String discoveryHash(LinkDiscoveryQuery query) {
        return sha256(normalize(query.searchText())
                + "|" + normalize(query.hostname())
                + "|" + normalize(query.tag())
                + "|" + query.lifecycle().name()
                + "|" + query.expiration().name()
                + "|" + query.sort().name()
                + "|" + query.limit()
                + "|" + normalize(query.cursor()));
    }
}
