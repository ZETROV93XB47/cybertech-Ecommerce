package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.response.cart.CartResponseDto;
import com.novatech.cybertech.services.core.CartCacheHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Redis-backed implementation of {@link CartCacheHelper}.
 * <p>
 * Lock keys: {@code lock:cart:&lt;keycloakId&gt;} with a 5s TTL — short enough that
 * a crashed worker cannot starve other workers, long enough that the typical
 * read-modify-write of {@code addItemsToCart} (one user lookup + one product
 * lookup + one save + one cache write) completes well within the budget.
 * <p>
 * Cache keys: {@code cart::&lt;keycloakId&gt;} with a TTL of
 * {@code baseTtlSeconds ± jitterMaxSeconds} to avoid stampede expirations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CartCacheHelperImp implements CartCacheHelper {

    private static final int LOCK_DURATION_IN_SECONDS = 5;
    /** Initial back-off between blocking-acquire retries (BUG-160). */
    private static final long BLOCKING_RETRY_INITIAL_MS = 5L;
    /** Cap on the back-off — keeps tail latency bounded. */
    private static final long BLOCKING_RETRY_MAX_MS = 50L;
    private static final String UNLOCK_SCRIPT = "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end";
    private static final DefaultRedisScript<Long> UNLOCK_REDIS_SCRIPT = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
    private static final StringRedisSerializer STRING_SERIALIZER = new StringRedisSerializer();

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.cache.max.ttl.jitter.time.seconds}")
    private int jitterMaxSeconds;

    @Value("${app.cache.default.ttl.expiration.time.seconds}")
    private int baseTtlSeconds;


    /**
     * Atomic non-blocking lock acquisition via {@code SET ... NX EX 5}.
     * <p>
     * Returns a freshly-minted token (UUID) on success or {@code null} if the
     * lock is already held by another worker. The token must be supplied to
     * {@link #releaseLock(String, String)} so the unlock Lua script can CAS-check
     * ownership before deleting the key — preventing the classic "release someone
     * else's lock after my TTL expired" race.
     */
    @Override
    public String acquireLock(final String userId) {
        final String lockKey = lockKey(userId);
        final String token = UUID.randomUUID().toString();

        log.info("Acquiring lock for user: {} with token: {}", userId, token);

        boolean success = Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(lockKey, token, Duration.ofSeconds(LOCK_DURATION_IN_SECONDS))
        );

        return success ? token : null;
    }


    /**
     * BUG-160 — Bounded-wait blocking acquisition. Spins with exponential
     * back-off (5ms → 50ms cap) on top of the existing non-blocking
     * {@link #acquireLock(String)}, returning the lock token on success or
     * {@code null} when {@code timeoutMillis} elapses without success.
     * <p>
     * Used by every cart write path so the entire DB load → mutate → save → cache
     * write happens inside the lock (otherwise concurrent {@code POST /cart/add}
     * requests can lose items via read-modify-write race).
     */
    @Override
    public String acquireLockBlocking(final String userId, final long timeoutMillis) {
        final long deadline = System.currentTimeMillis() + timeoutMillis;
        long backoff = BLOCKING_RETRY_INITIAL_MS;

        while (true) {
            final String token = acquireLock(userId);
            if (token != null) {
                return token;
            }

            if (System.currentTimeMillis() >= deadline) {
                log.warn("Timed out acquiring cart lock for user {} after {}ms", userId, timeoutMillis);
                return null;
            }

            try {
                Thread.sleep(backoff);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            }
            backoff = Math.min(BLOCKING_RETRY_MAX_MS, backoff * 2);
        }
    }


    /**
     * Token-checked unlock implemented as an atomic Lua script
     * ({@code GET == token ? DEL : noop}). Safe to call with a stale token —
     * the script will just be a no-op when the lock has already been re-acquired
     * by someone else after a TTL expiry.
     */
    @Override
    public void releaseLock(final String userId, final String token) {
        log.info("Releasing lock for user: {} with token: {}", userId, token);

        final String lockKey = lockKey(userId);

        redisTemplate.execute((RedisCallback<Long>) connection -> {
            byte[] keyBytes = STRING_SERIALIZER.serialize(lockKey);
            byte[] tokenBytes = STRING_SERIALIZER.serialize(token);

            return connection.eval(
                    UNLOCK_REDIS_SCRIPT.getScriptAsString().getBytes(StandardCharsets.UTF_8),
                    ReturnType.INTEGER,
                    1,
                    keyBytes,
                    tokenBytes
            );
        });
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
        return "lock:cart:" + userId;
    }

    // Symmetric jitter around baseTtlSeconds so writes at the same instant don't all expire together.
    private int getJitter() {
        if (jitterMaxSeconds <= 0) return 0;
        return ThreadLocalRandom.current().nextInt(-jitterMaxSeconds, jitterMaxSeconds + 1);
    }
}
