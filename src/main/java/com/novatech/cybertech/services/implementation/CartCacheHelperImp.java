package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.response.cart.CartResponseDto;
import com.novatech.cybertech.services.core.CartCacheHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class CartCacheHelperImp implements CartCacheHelper {

    private static final int LOCK_DURATION_IN_SECONDS = 5;
    public static final String LOCK_PLACEHOLDER = "1";

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.cache.max.ttl.jitter.time.seconds}")
    private static int JITTER_MAX_SECONDS;

    @Value("${app.cache.default.ttl.expiration.time.seconds}")
    private static int BASE_TTL_SECONDS;


    @Override
    public boolean acquireLock(final String userId) {
        log.info("Trying to acquire lock for the user : {}", userId);

        final String lockKey = "lock:cart:" + userId;
        boolean lockAcquisitionStatus = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, LOCK_PLACEHOLDER, Duration.ofSeconds(LOCK_DURATION_IN_SECONDS)));

        log.info("Lock acquisition status for user {}: {}", userId, lockAcquisitionStatus);

        return lockAcquisitionStatus;
    }

    @Override
    public void releaseLock(final String userId) {
        log.info("Releasing lock for the user : {}", userId);
        redisTemplate.delete("lock:cart:" + userId);
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