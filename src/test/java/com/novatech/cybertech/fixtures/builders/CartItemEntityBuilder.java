package com.novatech.cybertech.fixtures.builders;

import com.novatech.cybertech.entities.CartItemEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Test fixture builder for {@link CartItemEntity}. Presets {@code uuid} explicitly because builders
 * bypass {@code BaseEntity#prePersist}.
 */
public final class CartItemEntityBuilder {

    private CartItemEntityBuilder() {
    }

    public static CartItemEntity aValidCartItem() {
        return aValidCartItemBuilder().build();
    }

    public static CartItemEntity.CartItemEntityBuilder<?, ?> aValidCartItemBuilder() {
        return CartItemEntity.builder()
                .uuid(UUID.randomUUID())
                .quantity(1)
                .unitPrice(new BigDecimal("99.99"))
                .productEntity(ProductEntityBuilder.aValidProduct())
                .addedAt(LocalDateTime.now());
    }
}
