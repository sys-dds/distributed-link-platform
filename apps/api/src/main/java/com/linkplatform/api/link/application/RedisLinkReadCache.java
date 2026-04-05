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
    public Optional<Link> getPublicRedirect(String slug) {
        return readValue("redirect", redirectKey(slug), Link.class);
    }

    @Override
    public void putPublicRedirect(String slug, Link link) {
        writeValue("redirect", redirectKey(slug), link, redirectTtl);
    }

    @Override
    public void invalidatePublicRedirect(String slug) {
        delete("redirect", redirectKey(slug));
    }

    @Override
    public Optional<LinkDetails> getOwnerLinkDetails(long ownerId, String slug) {
        return readValue("detail", ownerControlPlaneKey(ownerId, "detail", slug), LinkDetails.class);
    }

    @Override
    public void putOwnerLinkDetails(long ownerId, String slug, LinkDetails linkDetails) {
        writeValue("detail", ownerControlPlaneKey(ownerId, "detail", slug), linkDetails, ownerReadTtl);
    }

    @Override
    public Optional<List<LinkDetails>> getOwnerRecentLinks(long ownerId, int limit, String query, LinkLifecycleState state) {
        return readValue("list", ownerControlPlaneKey(ownerId, "list", sha256(limit + "|" + state.name() + "|" + normalize(query))), LINK_DETAILS_LIST);
    }

    @Override
    public void putOwnerRecentLinks(long ownerId, int limit, String query, LinkLifecycleState state, List<LinkDetails> linkDetails) {
        writeValue("list", ownerControlPlaneKey(ownerId, "list", sha256(limit + "|" + state.name() + "|" + normalize(query))), linkDetails, ownerReadTtl);
    }

    @Override
    public Optional<List<LinkSuggestion>> getOwnerSuggestions(long ownerId, String query, int limit) {
        return readValue("suggestions", ownerControlPlaneKey(ownerId, "suggestions", sha256(limit + "|" + normalize(query))), LINK_SUGGESTION_LIST);
    }

    @Override
    public void putOwnerSuggestions(long ownerId, String query, int limit, List<LinkSuggestion> suggestions) {
        writeValue("suggestions", ownerControlPlaneKey(ownerId, "suggestions", sha256(limit + "|" + normalize(query))), suggestions, ownerReadTtl);
    }

    @Override
    public Optional<LinkDiscoveryPage> getOwnerDiscoveryPage(long ownerId, LinkDiscoveryQuery query) {
        return readValue("discovery", ownerControlPlaneKey(ownerId, "discovery", discoveryHash(query)), DISCOVERY_PAGE);
    }

    @Override
    public void putOwnerDiscoveryPage(long ownerId, LinkDiscoveryQuery query, LinkDiscoveryPage page) {
        writeValue("discovery", ownerControlPlaneKey(ownerId, "discovery", discoveryHash(query)), page, ownerReadTtl);
    }

    @Override
    public Optional<List<LinkActivityEvent>> getOwnerRecentActivity(long ownerId, int limit) {
        return readValue("activity", ownerAnalyticsKey(ownerId, "activity", String.valueOf(limit)), ACTIVITY_LIST);
    }

    @Override
    public void putOwnerRecentActivity(long ownerId, int limit, List<LinkActivityEvent> activityEvents) {
        writeValue("activity", ownerAnalyticsKey(ownerId, "activity", String.valueOf(limit)), activityEvents, analyticsTtl);
    }

    @Override
    public Optional<LinkTrafficSummary> getOwnerTrafficSummary(long ownerId, String slug) {
        return readValue("traffic_summary", ownerAnalyticsKey(ownerId, "traffic_summary", slug), LinkTrafficSummary.class);
    }

    @Override
    public void putOwnerTrafficSummary(long ownerId, String slug, LinkTrafficSummary summary) {
        writeValue("traffic_summary", ownerAnalyticsKey(ownerId, "traffic_summary", slug), summary, analyticsTtl);
    }

    @Override
    public Optional<List<TopLinkTraffic>> getOwnerTopLinks(long ownerId, LinkTrafficWindow window) {
        return readValue("top_links", ownerAnalyticsKey(ownerId, "top_links", window.name()), TOP_LINK_LIST);
    }

    @Override
    public void putOwnerTopLinks(long ownerId, LinkTrafficWindow window, List<TopLinkTraffic> topLinks) {
        writeValue("top_links", ownerAnalyticsKey(ownerId, "top_links", window.name()), topLinks, analyticsTtl);
    }

    @Override
    public Optional<List<TrendingLink>> getOwnerTrendingLinks(long ownerId, LinkTrafficWindow window, int limit) {
        return readValue("trending", ownerAnalyticsKey(ownerId, "trending", window.name() + "|" + limit), TRENDING_LIST);
    }

    @Override
    public void putOwnerTrendingLinks(long ownerId, LinkTrafficWindow window, int limit, List<TrendingLink> trendingLinks) {
        writeValue("trending", ownerAnalyticsKey(ownerId, "trending", window.name() + "|" + limit), trendingLinks, analyticsTtl);
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
            meterRegistry.counter("link.cache.hit", "area", area).increment();
            return Optional.of(objectMapper.readValue(value, type));
        } catch (Exception exception) {
            handleFailure("read", area, exception);
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
            meterRegistry.counter("link.cache.hit", "area", area).increment();
            return Optional.of(objectMapper.readValue(value, typeReference));
        } catch (Exception exception) {
            handleFailure("read", area, exception);
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

    private String ownerControlPlaneKey(long ownerId, String area, String suffix) {
        long generation = getGeneration(ownerControlPlaneGenerationKey(ownerId));
        return "link:owner:" + ownerId + ":cp:v" + generation + ":" + area + ":" + suffix;
    }

    private String ownerAnalyticsKey(long ownerId, String area, String suffix) {
        long generation = getGeneration(ownerAnalyticsGenerationKey(ownerId));
        return "link:owner:" + ownerId + ":analytics:v" + generation + ":" + area + ":" + suffix;
    }

    private String ownerControlPlaneGenerationKey(long ownerId) {
        return "link:owner:" + ownerId + ":cp:gen";
    }

    private String ownerAnalyticsGenerationKey(long ownerId) {
        return "link:owner:" + ownerId + ":analytics:gen";
    }

    private String redirectKey(String slug) {
        return "link:redirect:" + slug;
    }

    private long getGeneration(String generationKey) {
        try {
            String stored = redisTemplate.opsForValue().get(generationKey);
            return stored == null ? 0L : Long.parseLong(stored);
        } catch (Exception exception) {
            handleFailure("read_generation", "generation", exception);
            return 0L;
        }
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
