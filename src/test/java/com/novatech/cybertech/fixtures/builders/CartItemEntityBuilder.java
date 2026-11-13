package com.novatech.cybertech.fixtures.builders;

import com.novatech.cybertech.entities.CartItemEntity;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Test fixture builder for {@link CartItemEntity}. Presets {@code uuid} explicitly because builders
 * bypass {@code BaseEntity#prePersist}.
 *
 * <p>No price on the cart item itself — {@link CartItemEntity} carries no frozen price, the line's
 * price is always the linked {@link ProductEntityBuilder#aValidProduct() product}'s live price
 * (defaults to 99.99).
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
                .productEntity(ProductEntityBuilder.aValidProduct())
                .addedAt(LocalDateTime.now());
    }
}
