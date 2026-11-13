package com.novatech.cybertech.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * Makes the cache a pure optimisation: a failing cache operation is logged and the call proceeds
 * as if the cache were absent, instead of surfacing as a 500.
 *
 * <p>On a failed read the entry is also evicted, so that the value recomputed by the
 * {@code @Cacheable} method overwrites it. Without this, an entry written in a format the current
 * serializer cannot read (e.g. after a serializer change: {@code discountCampaigns} entries written
 * with {@code @class} hints, read back by a typed serializer) broke every {@code placeOrder} using
 * that discount until the entry's TTL expired.
 */
@Slf4j
public class SelfHealingCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(final RuntimeException exception, final Cache cache, final Object key) {
        log.warn("Unreadable entry {}::{} — treated as a cache miss and evicted: {}", cache.getName(), key, exception.getMessage());
        try {
            cache.evict(key);
        } catch (final RuntimeException evictFailure) {
            log.warn("Could not evict {}::{}: {}", cache.getName(), key, evictFailure.getMessage());
        }
    }

    @Override
    public void handleCachePutError(final RuntimeException exception, final Cache cache, final Object key, final Object value) {
        log.warn("Could not cache {}::{} — value served uncached: {}", cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheEvictError(final RuntimeException exception, final Cache cache, final Object key) {
        log.warn("Could not evict {}::{}: {}", cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheClearError(final RuntimeException exception, final Cache cache) {
        log.warn("Could not clear cache {}: {}", cache.getName(), exception.getMessage());
    }
}
