package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.response.cart.CartResponseDto;
import com.novatech.cybertech.services.core.CartCacheHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed implementation of {@link CartCacheHelper}.
 * <p>
 * <b>Locking</b> delegates entirely to Redisson's {@link RLock} on key
 * {@code cart:lock:<keycloakId>}: {@code tryLock(waitTime, unit)} (no explicit lease
 * time) hands the lock a "watchdog" that keeps extending its TTL in the background for
 * as long as the owning client is alive, instead of a fixed TTL that could expire mid
 * read-modify-write on a slow request. Ownership is tracked by Redisson internally
 * (thread id), so unlocking never needs a token to be threaded through the call —
 * {@link #releaseLock} just needs to run on the same thread that acquired the lock,
 * which is guaranteed by {@code CartServiceImp}'s try/finally usage.
 * <p>
 * <b>Cache</b> reads/writes stay on the plain {@link RedisTemplate} at
 * {@code cart::<keycloakId>} with a TTL of {@code baseTtlSeconds ± jitterMaxSeconds} to
 * avoid stampede expirations — this part never needed the lock's complexity and is
 * unchanged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CartCacheHelperImp implements CartCacheHelper {

    private static final String CART_LOCK_PREFIX = "cart:lock:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;

    @Value("${app.cache.max.ttl.jitter.time.seconds}")
    private int jitterMaxSeconds;

    @Value("${app.cache.default.ttl.expiration.time.seconds}")
    private int baseTtlSeconds;

    @Override
    public boolean acquireLock(final String userId) {
        return tryLock(userId, 0L);
    }

    @Override
    public boolean acquireLockBlocking(final String userId, final long timeoutMillis) {
        return tryLock(userId, timeoutMillis);
    }

    private boolean tryLock(final String userId, final long waitMillis) {
        final RLock lock = redissonClient.getLock(lockKey(userId));
        try {
            final boolean acquired = lock.tryLock(waitMillis, TimeUnit.MILLISECONDS);
            if (!acquired) {
                log.warn("Could not acquire cart lock for user {} within {}ms", userId, waitMillis);
            }
            return acquired;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void releaseLock(final String userId) {
        final RLock lock = redissonClient.getLock(lockKey(userId));
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /**
     * Slides the existing cache entry's TTL forward by a fresh
     * {@code base ± jitter} interval. Called on cache hits so frequently-read
     * carts stay warm but the absolute expiry timing remains de-synchronised
     * across users (anti-stampede).
     */
    @Override
    public void refreshTtlWithJitter(final String userId) {
        log.info("Refreshing TTL with jitter for user: {}", userId);
        redisTemplate.expire(cartKey(userId), getTtlWithJitter());
    }

    /**
     * Writes the cart DTO to {@code cart::<userId>} with a jittered TTL.
     */
    @Override
    public void putWithJitter(final String userId, final CartResponseDto cart) {
        log.info("Putting cart in cache with jitter for user: {}", userId);
        redisTemplate.opsForValue().set(cartKey(userId), cart, getTtlWithJitter());
    }

    private Duration getTtlWithJitter() {
        return Duration.ofSeconds(Math.max(1, baseTtlSeconds + getJitter()));
    }

    /**
     * Raw cache read — does <em>not</em> refresh the TTL. The caller decides
     * whether a hit warrants a sliding refresh.
     */
    @Override
    public CartResponseDto getRaw(final String userId) {
        log.info("Getting raw cart from cache for user: {}", userId);
        return (CartResponseDto) redisTemplate.opsForValue().get(cartKey(userId));
    }

    private static String cartKey(final String userId) {
        return "cart::" + userId;
    }

    private static String lockKey(final String userId) {
        return CART_LOCK_PREFIX + userId;
    }

    // Symmetric jitter around baseTtlSeconds so writes at the same instant don't all expire together.
    private int getJitter() {
        if (jitterMaxSeconds <= 0) return 0;
        return ThreadLocalRandom.current().nextInt(-jitterMaxSeconds, jitterMaxSeconds + 1);
    }
}
