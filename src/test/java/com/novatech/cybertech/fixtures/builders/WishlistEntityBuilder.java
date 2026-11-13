package com.novatech.cybertech.fixtures.builders;

import com.novatech.cybertech.entities.WishlistEntity;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Test fixture builder for {@link WishlistEntity}. Presets {@code uuid} explicitly because builders
 * bypass {@code BaseEntity#prePersist}.
 */
public final class WishlistEntityBuilder {

    private WishlistEntityBuilder() {
    }

    public static WishlistEntity aValidWishlist() {
        return aValidWishlistBuilder().build();
    }

    public static WishlistEntity.WishlistEntityBuilder<?, ?> aValidWishlistBuilder() {
        return WishlistEntity.builder()
                .uuid(UUID.randomUUID())
                .user(UserEntityBuilder.aValidUser())
                .product(ProductEntityBuilder.aValidProduct())
                .addedAt(LocalDateTime.now());
    }
}
