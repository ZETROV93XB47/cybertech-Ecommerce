package com.novatech.cybertech.fixtures.builders;

import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.enums.DiscountType;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.entities.valueObjects.Address;
import com.novatech.cybertech.entities.valueObjects.CurrencyCode;
import com.novatech.cybertech.entities.valueObjects.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Test fixture builder for {@link OrderEntity}. Presets {@code uuid} explicitly because builders bypass
 * {@code BaseEntity#prePersist}. Mutable empty lists are wired for {@code orderItemEntities} and
 * {@code paymentAttempts} so callers can append without NPE.
 */
public final class OrderEntityBuilder {

    private OrderEntityBuilder() {
    }

    public static OrderEntity aValidOrder() {
        return aValidOrderBuilder().build();
    }

    public static OrderEntity.OrderEntityBuilder<?, ?> aValidOrderBuilder() {
        return OrderEntity.builder()
                .uuid(UUID.randomUUID())
                .orderDate(LocalDateTime.now())
                .totalAmount(new Money(new BigDecimal("100.00"), CurrencyCode.EUR))
                .shippingAddress(Address.builder()
                        .street("1 rue de Test")
                        .city("Paris")
                        .zipCode("75001")
                        .country("FR")
                        .build())
                .status(OrderStatus.CREATED)
                .shippingType(ShippingType.STANDARD)
                .shippingProvider(ShippingProvider.DHL)
                .discountType(DiscountType.NO_DISCOUNT)
                .orderItemEntities(new ArrayList<>())
                .paymentAttempts(new ArrayList<>());
    }

    /**
     * Default {@link PaymentType} kept here for convenience when callers need one matching a payment attempt.
     */
    public static PaymentType defaultPaymentType() {
        return PaymentType.VISA;
    }
}
