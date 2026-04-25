package com.novatech.cybertech.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.novatech.cybertech.dto.response.cart.CartResponseDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableRedisRepositories
public class RedisConfig {

    private static final String CART_CACHE = "cart";
    private static final String USER_EXISTENCE_CACHE = "userExistence";

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory, @Qualifier("redisObjectMapper") final ObjectMapper redisObjectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJacksonJsonRedisSerializer jsonSerializer = new GenericJacksonJsonRedisSerializer(redisObjectMapper);

        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }


    @Bean("redisObjectMapper")
    public ObjectMapper redisObjectMapper() {
        // GenericJacksonJsonRedisSerializer relies on @class type hints to
        // reconstruct domain objects on read. Without DefaultTyping every cached
        // object would silently come back as a LinkedHashMap.
        //
        // Wave 3 regression-fix: tighten the polymorphic type validator from a blanket
        // `allowIfBaseType(Object.class)` to an explicit per-class allowlist of cached
        // domain DTOs and the JDK collection / wrapper types that show up inside their
        // serialised graphs. Allowing arbitrary base types lets a malicious cache writer
        // (or a poisoned cache row) trigger gadget-chain deserialisation on read; the
        // whitelist below limits the attack surface to types we actually round-trip.
        final PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                // Cached domain DTOs — the actual @Cacheable / putWithJitter payloads.
                .allowIfSubType(com.novatech.cybertech.dto.response.cart.CartResponseDto.class)
                .allowIfSubType(com.novatech.cybertech.dto.response.cart.CartItemResponseDto.class)
                .allowIfSubType(com.novatech.cybertech.dto.data.DiscountContext.class)
                // Domain enums embedded in the DTOs above.
                .allowIfSubType(com.novatech.cybertech.entities.enums.DiscountType.class)
                .allowIfSubType(com.novatech.cybertech.entities.enums.DiscountCalculationType.class)
                // JDK collection / wrapper types referenced by the DTO graphs (CartResponseDto.items
                // is a List<CartItemResponseDto>, DiscountContext fields are LocalDateTime/BigDecimal,
                // userExistence cache stores Boolean, etc.).
                .allowIfSubType(java.util.List.class)
                .allowIfSubType(java.util.Map.class)
                .allowIfSubType(java.util.Set.class)
                .allowIfSubType(java.util.Collection.class)
                .allowIfSubType(java.util.UUID.class)
                .allowIfSubType(java.math.BigDecimal.class)
                .allowIfSubType(java.time.LocalDateTime.class)
                .allowIfSubType(Boolean.class)
                .allowIfSubType(String.class)
                .allowIfSubType(Number.class)
                .build();

        return JsonMapper.builder()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .activateDefaultTyping(ptv, DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY)
                .build();
    }


    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            RedisCacheConfiguration baseConfig,
            @Value("${app.cache.default.ttl.expiration.time.seconds}") int baseTtlSeconds
    ) {

        final RedisCacheConfiguration defaultConfig = baseConfig.entryTtl(Duration.ofHours(1));

        final JacksonJsonRedisSerializer<Boolean> userExistSerializer = new org.springframework.data.redis.serializer.JacksonJsonRedisSerializer<>(Boolean.class);
        final JacksonJsonRedisSerializer<CartResponseDto> cartSerializer = new org.springframework.data.redis.serializer.JacksonJsonRedisSerializer<>(CartResponseDto.class);

        final RedisCacheConfiguration cartConfig = defaultConfig
                .entryTtl(Duration.ofSeconds(baseTtlSeconds))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(cartSerializer));

        final RedisCacheConfiguration userExistConfig = defaultConfig
                .entryTtl(Duration.ofHours(24))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(userExistSerializer));

        final Map<String, RedisCacheConfiguration> configs = new HashMap<>();

        configs.put(CART_CACHE, cartConfig);
        configs.put(USER_EXISTENCE_CACHE, userExistConfig);

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(configs)
                .build();
    }

    @Bean
    public RedisCacheConfiguration cacheConfiguration(
            @Qualifier("redisObjectMapper") final ObjectMapper redisObjectMapper,
            @Value("${app.cache.default.ttl.expiration.time.seconds}") int baseTtlSeconds
    ) {
        return RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(baseTtlSeconds))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJacksonJsonRedisSerializer(redisObjectMapper)));
    }
}