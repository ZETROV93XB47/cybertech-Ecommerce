package com.novatech.cybertech.fixtures.builders;

import com.novatech.cybertech.entities.OrderItemEntity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Test fixture builder for {@link OrderItemEntity}. Presets {@code uuid} explicitly because builders
 * bypass {@code BaseEntity#prePersist}.
 */
public final class OrderItemEntityBuilder {

    private OrderItemEntityBuilder() {
    }

    public static OrderItemEntity aValidOrderItem() {
        return aValidOrderItemBuilder().build();
    }

    public static OrderItemEntity.OrderItemEntityBuilder<?, ?> aValidOrderItemBuilder() {
        return OrderItemEntity.builder()
                .uuid(UUID.randomUUID())
                .quantity(1)
                .unitPrice(new BigDecimal("99.99"))
                .subtotal(new BigDecimal("99.99"))
                .productEntity(ProductEntityBuilder.aValidProduct());
    }
}
