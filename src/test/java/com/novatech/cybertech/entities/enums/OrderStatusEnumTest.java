package com.novatech.cybertech.entities.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusEnumTest {

    @Test
    void hasExpectedConstantCount() {
        assertThat(OrderStatus.values()).hasSize(10);
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void everyStatusHasNonNullCode(final OrderStatus status) {
        assertThat(status.getCode()).isNotNull().isPositive();
    }

    @Test
    void codesAreUniqueAndContiguousFromOne() {
        final var codes = Arrays.stream(OrderStatus.values()).map(OrderStatus::getCode).toList();

        assertThat(codes)
                .doesNotHaveDuplicates()
                .allMatch(c -> c >= 1 && c <= OrderStatus.values().length);
    }

    @Test
    void canonicalCodeMapping() {
        // Pin each constant -> code so a renumbering accident is loud.
        assertThat(OrderStatus.CREATED.getCode()).isEqualTo(1);
        assertThat(OrderStatus.AWAITING_PAYMENT.getCode()).isEqualTo(2);
        assertThat(OrderStatus.PAYMENT_FAILED.getCode()).isEqualTo(3);
        assertThat(OrderStatus.PAID.getCode()).isEqualTo(4);
        assertThat(OrderStatus.AWAITING_SHIPPING.getCode()).isEqualTo(5);
        assertThat(OrderStatus.SHIPPED.getCode()).isEqualTo(6);
        assertThat(OrderStatus.DELIVERED.getCode()).isEqualTo(7);
        assertThat(OrderStatus.RETURNED.getCode()).isEqualTo(8);
        assertThat(OrderStatus.CANCELED.getCode()).isEqualTo(9);
        assertThat(OrderStatus.REFUNDED.getCode()).isEqualTo(10);
    }
}
