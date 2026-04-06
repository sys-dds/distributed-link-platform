package com.linkplatform.api.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.linkplatform.api.owner.application.SecurityEventStore;
import com.linkplatform.api.owner.application.SecurityEventType;
import com.linkplatform.api.runtime.LinkPlatformRuntimeProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class RedirectRateLimitServiceTest {

    @Test
    void rejectsRepeatedAbuseAndFallsBackWhenPrimaryStoreDegrades() {
        CountingStore fallbackStore = new CountingStore();
        FailingRedisStore redisStore = new FailingRedisStore();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("redisRedirectRateLimitStore", redisStore);
        LinkPlatformRuntimeProperties properties = new LinkPlatformRuntimeProperties();
        properties.getRedirect().getRateLimit().setEnabled(true);
        properties.getRedirect().getRateLimit().setRequestsPerWindow(2);
        RedirectRateLimitService service = new RedirectRateLimitService(
                fallbackStore,
                beanFactory.getBeanProvider(RedisRedirectRateLimitStore.class),
                new TestSecurityEventStore(),
                properties,
                new SimpleMeterRegistry());

        assertThat(service.check("docs", "/docs", "127.0.0.1").allowed()).isTrue();
        assertThat(service.check("docs", "/docs", "127.0.0.1").allowed()).isTrue();
        RedirectRateLimitDecision rejected = service.check("docs", "/docs", "127.0.0.1");

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.fallbackStoreUsed()).isTrue();
        assertThat(rejected.degradedStore()).isTrue();
        assertThat(service.getLastStoreMode()).isEqualTo("jdbc-fallback");
    }

    private static final class CountingStore implements RedirectRateLimitStore {
        private final AtomicInteger counter = new AtomicInteger();
        @Override public int increment(String subjectHash, String slug, OffsetDateTime bucketStartedAt, Duration window, OffsetDateTime expiresAt) { return counter.incrementAndGet(); }
    }

    private static final class FailingRedisStore extends RedisRedirectRateLimitStore {
        FailingRedisStore() { super(null); }
        @Override public int increment(String subjectHash, String slug, OffsetDateTime bucketStartedAt, Duration window, OffsetDateTime expiresAt) { throw new RuntimeException("redis down"); }
    }

    private static final class TestSecurityEventStore implements SecurityEventStore {
        private final List<SecurityEventType> types = new ArrayList<>();
        @Override public void record(SecurityEventType eventType, Long ownerId, String apiKeyHash, String requestMethod, String requestPath, String remoteAddress, String detailSummary, OffsetDateTime occurredAt) { types.add(eventType); }
    }
}
