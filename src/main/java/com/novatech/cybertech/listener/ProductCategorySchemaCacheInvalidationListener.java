package com.novatech.cybertech.listener;

import com.novatech.cybertech.services.core.ProductCategorySchemaCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Cross-instance cache invalidation for {@link ProductCategorySchemaCache}: every instance
 * subscribes to {@code CyberTechAppConstants.PRODUCT_CATEGORY_SCHEMA_CHANGED_CHANNEL} (registered
 * on the shared {@code redisContainer} bean in {@code AppConfig}); the admin service publishes the
 * changed {@code categoryKey} after every create/update/delete. A message body is the raw
 * categoryKey string — published via {@code StringRedisTemplate#convertAndSend}, so it's decoded
 * here as plain UTF-8 rather than through Spring's polymorphic-typed serializer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCategorySchemaCacheInvalidationListener implements MessageListener {

    private final ProductCategorySchemaCache productCategorySchemaCache;

    @Override
    public void onMessage(final Message message, final byte[] pattern) {
        final String categoryKey = new String(message.getBody(), StandardCharsets.UTF_8);
        log.debug("Invalidating product category schema cache entry for {}", categoryKey);
        productCategorySchemaCache.invalidate(categoryKey);
    }
}
