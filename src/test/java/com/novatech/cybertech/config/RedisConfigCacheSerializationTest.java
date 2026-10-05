package com.novatech.cybertech.config;

import com.novatech.cybertech.dto.data.DiscountContext;
import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.SerializationException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static com.novatech.cybertech.constants.CyberTechAppConstants.DISCOUNT_CAMPAIGNS_CACHE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Round-trips the cached payloads through the serializers {@link RedisConfig} actually wires,
 * without a Redis server: a cache write that cannot be read back only fails on the SECOND call
 * (first cache hit), which no slice test exercising a single call would catch.
 */
class RedisConfigCacheSerializationTest {

    private static final int TTL_SECONDS = 60;

    private final RedisConfig redisConfig = new RedisConfig();
    private final ObjectMapper redisObjectMapper = redisConfig.redisObjectMapper();
    private final RedisCacheConfiguration defaultConfig = redisConfig.cacheConfiguration(redisObjectMapper, TTL_SECONDS);

    private static DiscountContext blackFriday() {
        return DiscountContext.builder()
                .discountKey("BLACK_FRIDAY")
                .calculationType(DiscountCalculationType.PERCENTAGE)
                .percentage(new BigDecimal("20.00"))
                .minOrderAmount(new BigDecimal("50.00"))
                .maxDiscountAmount(new BigDecimal("200.00"))
                .startsAt(LocalDateTime.of(2026, 11, 27, 0, 0))
                .endsAt(LocalDateTime.of(2026, 11, 30, 23, 59))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Object roundTrip(final SerializationPair<?> pair, final Object value) {
        final ByteBuffer written = ((SerializationPair<Object>) pair).write(value);
        return pair.read(written);
    }

    @Test
    void discountCampaignsCacheReadsBackTheDiscountContextItWrote() {
        final RedisCacheManager cacheManager = redisConfig.cacheManager(mock(RedisConnectionFactory.class), defaultConfig, TTL_SECONDS);
        cacheManager.afterPropertiesSet(); // as the container does — registers the initial caches
        final RedisCacheConfiguration discountConfig = cacheManager.getCacheConfigurations().get(DISCOUNT_CAMPAIGNS_CACHE);

        assertThat(discountConfig).as("dedicated configuration for the %s cache", DISCOUNT_CAMPAIGNS_CACHE).isNotNull();
        assertThat(roundTrip(discountConfig.getValueSerializationPair(), blackFriday())).isEqualTo(blackFriday());
    }

    @Test
    void entryWrittenByTheFormerDefaultSerializerIsUnreadableByTheTypedOne() {
        // Exact payload found in Redis after the serializer switch (2026-10-05). Unreadable by
        // design — SelfHealingCacheErrorHandler is what turns it into a cache miss instead of a 500.
        final String formerPayload = "{\"discountKey\":\"BLACK_FRIDAY\",\"calculationType\":\"PERCENTAGE\","
                + "\"percentage\":[\"java.math.BigDecimal\",20.00],\"fixedAmount\":null,\"minOrderAmount\":null,"
                + "\"maxDiscountAmount\":null,\"startsAt\":null,\"endsAt\":null}";
        final RedisCacheManager cacheManager = redisConfig.cacheManager(mock(RedisConnectionFactory.class), defaultConfig, TTL_SECONDS);
        cacheManager.afterPropertiesSet();
        final SerializationPair<?> discountPair = cacheManager.getCacheConfigurations().get(DISCOUNT_CAMPAIGNS_CACHE).getValueSerializationPair();

        assertThatThrownBy(() -> discountPair.read(ByteBuffer.wrap(formerPayload.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(SerializationException.class);
    }

    @Test
    void defaultCacheSerializerCannotReadBackARecord() {
        // Pins WHY the discount cache needs its own serializer: DefaultTyping.NON_FINAL writes no
        // @class hint for a final type (every record is final), so the read side has no type id.
        assertThatThrownBy(() -> roundTrip(defaultConfig.getValueSerializationPair(), blackFriday()))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("@class");
    }
}
