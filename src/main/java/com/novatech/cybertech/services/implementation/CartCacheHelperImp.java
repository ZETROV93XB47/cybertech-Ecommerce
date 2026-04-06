package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.response.cart.CartResponseDto;
import com.novatech.cybertech.services.core.CartCacheHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class CartCacheHelperImp implements CartCacheHelper {

    private static final int LOCK_DURATION_IN_SECONDS = 5;
    public static final String LOCK_PLACEHOLDER = "1";
    private static final String UNLOCK_SCRIPT = "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end";


    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.cache.max.ttl.jitter.time.seconds}")
    private static int JITTER_MAX_SECONDS;

    @Value("${app.cache.default.ttl.expiration.time.seconds}")
    private static int BASE_TTL_SECONDS;


    @Override
    public String acquireLock(String userId) {
        String lockKey = "lock:cart:" + userId;
        String token = UUID.randomUUID().toString();

        log.info("Acquiring lock for the user : {} with the following token : {}", userId, token);

        boolean success = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, token, Duration.ofSeconds(5)));

        return success ? token : null;
    }


    @Override
    public void releaseLock(final String userId, final String token) {
        log.info("Releasing lock for the user : {} with the following token : {}", userId, token);

        final String lockKey = "lock:cart:" + userId;
        final DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
        final StringRedisSerializer stringSerializer = new StringRedisSerializer();

        log.info("Executing Lua script to release lock for the user : {} with the following token : {}", userId, token);

        redisTemplate.execute((RedisCallback<Long>) connection -> {
            byte[] keyBytes = stringSerializer.serialize(lockKey);
            byte[] tokenBytes = stringSerializer.serialize(token);

            return connection.eval(
                    script.getScriptAsString().getBytes(StandardCharsets.UTF_8),
                    ReturnType.INTEGER,
                    1,          // nombre de KEYS
                    keyBytes,
                    tokenBytes
            );
        });
    }

    @Override
    public void refreshTtlWithJitter(final String userId) {

        log.info("Refreshing TTL with jitter for user: {}", userId);

        int jitter = ThreadLocalRandom.current().nextInt(0, JITTER_MAX_SECONDS);
        redisTemplate.expire("cart::" + userId, Duration.ofSeconds(BASE_TTL_SECONDS + jitter));
    }

    @Override
    public void putWithJitter(final String userId, final CartResponseDto cart) {

        log.info("Putting cart in cache with jitter for user: {}", userId);

        int jitter = ThreadLocalRandom.current().nextInt(0, JITTER_MAX_SECONDS);
        redisTemplate.opsForValue().set(
                "cart::" + userId,
                cart,
                Duration.ofSeconds(BASE_TTL_SECONDS + jitter)
        );
    }

    @Override
    public CartResponseDto getRaw(final String userId) {

        log.info("Getting raw cart from cache for user: {}", userId);

        return (CartResponseDto) redisTemplate.opsForValue().get("cart::" + userId);
    }
}