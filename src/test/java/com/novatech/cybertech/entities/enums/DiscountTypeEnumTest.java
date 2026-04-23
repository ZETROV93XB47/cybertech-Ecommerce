package com.novatech.cybertech.entities.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class DiscountTypeEnumTest {

    @Test
    void hasFiveDiscountTypes() {
        assertThat(DiscountType.values()).hasSize(5);
    }

    @ParameterizedTest
    @EnumSource(DiscountType.class)
    void discountPercentageIsAlwaysWithinPlausibleBounds(final DiscountType type) {
        // All current discount values are >= 0 and <= 1.
        assertThat(type.getDiscountPercentage()).isBetween(0f, 1f);
    }

    @Test
    void canonicalDiscountMapping() {
        // Pin business semantics — these floats drive promo math.
        assertThat(DiscountType.NO_DISCOUNT.getDiscountPercentage()).isEqualTo(1f);
        assertThat(DiscountType.BUY_ONE_GET_ONE_FREE.getDiscountPercentage()).isEqualTo(1f);
        assertThat(DiscountType.BLACK_FRIDAY.getDiscountPercentage()).isEqualTo(0.4f);
        assertThat(DiscountType.WINTER_SALES.getDiscountPercentage()).isEqualTo(0.2f);
        assertThat(DiscountType.SPRING_SALES.getDiscountPercentage()).isEqualTo(0.3f);
    }
}
