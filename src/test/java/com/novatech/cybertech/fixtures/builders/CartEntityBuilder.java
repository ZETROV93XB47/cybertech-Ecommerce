package com.novatech.cybertech.fixtures.builders;

import com.novatech.cybertech.entities.CartEntity;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Test fixture builder for {@link CartEntity}. Presets {@code uuid} explicitly and wires a mutable
 * empty {@code cartItems} list to avoid NPE footguns when callers append items.
 */
public final class CartEntityBuilder {

    private CartEntityBuilder() {
    }

    public static CartEntity aValidCart() {
        return aValidCartBuilder().build();
    }

    public static CartEntity.CartEntityBuilder<?, ?> aValidCartBuilder() {
        return CartEntity.builder()
                .uuid(UUID.randomUUID())
                .userEntity(UserEntityBuilder.aValidUser())
                .cartItems(new ArrayList<>());
    }
}
