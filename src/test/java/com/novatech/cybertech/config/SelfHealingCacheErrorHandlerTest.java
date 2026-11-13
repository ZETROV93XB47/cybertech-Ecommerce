package com.novatech.cybertech.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.SerializationException;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SelfHealingCacheErrorHandlerTest {

    private static final String CACHE_NAME = "discountCampaigns";
    private static final String KEY = "BLACK_FRIDAY";
    private static final String STALE_VALUE = "stale";
    private static final String FRESH_VALUE = "fresh";

    private final SelfHealingCacheErrorHandler handler = new SelfHealingCacheErrorHandler();

    @Test
    void getErrorEvictsTheUnreadableEntryWithoutRethrowing() {
        final Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn(CACHE_NAME);

        assertThatCode(() -> handler.handleCacheGetError(new SerializationException("bad payload"), cache, KEY))
                .doesNotThrowAnyException();
        verify(cache).evict(KEY);
    }

    @Test
    void getErrorSurvivesAFailingEviction() {
        // Redis unreachable: the read failed AND the evict fails — the call must still go on uncached.
        final Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn(CACHE_NAME);
        doThrow(new IllegalStateException("redis down")).when(cache).evict(KEY);

        assertThatCode(() -> handler.handleCacheGetError(new IllegalStateException("redis down"), cache, KEY))
                .doesNotThrowAnyException();
    }

    @Test
    void putEvictAndClearErrorsAreSwallowed() {
        final Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn(CACHE_NAME);
        final RuntimeException failure = new IllegalStateException("redis down");

        assertThatCode(() -> {
            handler.handleCachePutError(failure, cache, KEY, FRESH_VALUE);
            handler.handleCacheEvictError(failure, cache, KEY);
            handler.handleCacheClearError(failure, cache);
        }).doesNotThrowAnyException();
    }

    @Test
    void unreadableEntryIsRecomputedAndOverwrittenThroughTheCacheProxy() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ProxyConfig.class)) {
            final CachedLookup lookup = context.getBean(CachedLookup.class);
            final PoisonableCache cache = context.getBean(PoisonableCache.class);
            cache.put(KEY, STALE_VALUE);
            cache.poisoned = true;

            assertThat(lookup.find(KEY)).isEqualTo(FRESH_VALUE);
            assertThat(lookup.calls()).isEqualTo(1);

            cache.poisoned = false;
            assertThat(cache.get(KEY).get()).as("the fresh value replaced the unreadable entry").isEqualTo(FRESH_VALUE);
        }
    }

    /** A cache whose reads fail like a Redis entry the current serializer cannot decode. */
    static class PoisonableCache extends ConcurrentMapCache {

        volatile boolean poisoned;

        PoisonableCache() {
            super(CACHE_NAME);
        }

        @Override
        protected Object lookup(final Object key) {
            if (poisoned) {
                throw new SerializationException("Could not read JSON");
            }
            return super.lookup(key);
        }
    }

    static class CachedLookup {

        private final AtomicInteger calls = new AtomicInteger();

        @Cacheable(CACHE_NAME)
        public String find(final String key) {
            calls.incrementAndGet();
            return FRESH_VALUE;
        }

        // Accessed through a method: the bean is a CGLIB proxy whose own fields are never set.
        public int calls() {
            return calls.get();
        }
    }

    @Configuration
    @EnableCaching
    static class ProxyConfig implements CachingConfigurer {

        @Bean
        PoisonableCache poisonableCache() {
            return new PoisonableCache();
        }

        @Bean
        @Override
        public CacheManager cacheManager() {
            final SimpleCacheManager manager = new SimpleCacheManager();
            manager.setCaches(List.of(poisonableCache()));
            return manager;
        }

        @Override
        public CacheErrorHandler errorHandler() {
            return new SelfHealingCacheErrorHandler();
        }

        @Bean
        CachedLookup cachedLookup() {
            return new CachedLookup();
        }
    }
}
