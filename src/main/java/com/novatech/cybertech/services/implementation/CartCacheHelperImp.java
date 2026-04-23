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

@Slf4j
@Component
@RequiredArgsConstructor
public class CartCacheHelperImp implements CartCacheHelper {

    private static final int LOCK_DURATION_IN_SECONDS = 5;
    private static final String UNLOCK_SCRIPT = "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end";
    private static final DefaultRedisScript<Long> UNLOCK_REDIS_SCRIPT = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
    private static final StringRedisSerializer STRING_SERIALIZER = new StringRedisSerializer();

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.cache.max.ttl.jitter.time.seconds}")
    private int jitterMaxSeconds;

    @Value("${app.cache.default.ttl.expiration.time.seconds}")
    private int baseTtlSeconds;


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

    @Override
    public void refreshTtlWithJitter(final String userId) {
        log.info("Refreshing TTL with jitter for user: {}", userId);
        redisTemplate.expire(cartKey(userId), getTtlWithJitter());
    }

    @Override
    public void putWithJitter(final String userId, final CartResponseDto cart) {
        log.info("Putting cart in cache with jitter for user: {}", userId);
        redisTemplate.opsForValue().set(cartKey(userId), cart, getTtlWithJitter());
    }

    private Duration getTtlWithJitter() {
        return Duration.ofSeconds(Math.max(1, baseTtlSeconds + getJitter()));
    }

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
