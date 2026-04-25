package com.novatech.cybertech.strategy.discount;

import com.novatech.cybertech.dto.data.DiscountContext;
import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import com.novatech.cybertech.entities.enums.DiscountType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixedAmountDiscountStrategyTest {

    private final FixedAmountDiscountStrategy strategy = new FixedAmountDiscountStrategy();

    private DiscountContext context(final BigDecimal fixedAmount) {
        return DiscountContext.builder()
                .discountType(DiscountType.NO_DISCOUNT)
                .calculationType(DiscountCalculationType.FIXED_AMOUNT)
                .fixedAmount(fixedAmount)
                .build();
    }

    @Test
    @DisplayName("base 100, fixed 15 => 15 discount")
    void appliesFixedAmount() {
        final BigDecimal result = strategy.calculateDiscount(
                new BigDecimal("100.00"), List.of(), context(new BigDecimal("15.00")));
        assertThat(result).isEqualByComparingTo("15.00");
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("fixed exceeds base => discount capped at base (final amount cannot go negative)")
    void capsAtBase() {
        final BigDecimal result = strategy.calculateDiscount(
                new BigDecimal("10.00"), List.of(), context(new BigDecimal("50.00")));
        assertThat(result).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("maxDiscountAmount caps the discount")
    void respectsMaxCap() {
        final DiscountContext capped = DiscountContext.builder()
                .discountType(DiscountType.NO_DISCOUNT)
                .calculationType(DiscountCalculationType.FIXED_AMOUNT)
                .fixedAmount(new BigDecimal("50.00"))
                .maxDiscountAmount(new BigDecimal("20.00"))
                .build();
        final BigDecimal result = strategy.calculateDiscount(
                new BigDecimal("100.00"), List.of(), capped);
        assertThat(result).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("null fixedAmount => IllegalStateException")
    void nullFixedAmountThrows() {
        assertThatThrownBy(() -> strategy.calculateDiscount(
                new BigDecimal("100.00"), List.of(), context(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-null fixedAmount");
    }
}
