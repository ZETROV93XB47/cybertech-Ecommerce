package com.novatech.cybertech.strategy.discount;

import com.novatech.cybertech.dto.data.DiscountContext;
import com.novatech.cybertech.dto.request.order.OrderItemPriceDto;
import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import com.novatech.cybertech.entities.enums.DiscountType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PercentageDiscountStrategyTest {

    private final PercentageDiscountStrategy strategy = new PercentageDiscountStrategy();

    private DiscountContext context(final BigDecimal percentage) {
        return DiscountContext.builder()
                .discountType(DiscountType.BLACK_FRIDAY)
                .calculationType(DiscountCalculationType.PERCENTAGE)
                .percentage(percentage)
                .build();
    }

    private DiscountContext contextWithCap(final BigDecimal percentage, final BigDecimal cap) {
        return DiscountContext.builder()
                .discountType(DiscountType.BLACK_FRIDAY)
                .calculationType(DiscountCalculationType.PERCENTAGE)
                .percentage(percentage)
                .maxDiscountAmount(cap)
                .build();
    }

    @Test
    @DisplayName("100.00 base @ 20% => 20.00 discount, scale 2")
    void appliesPercentage() {
        final BigDecimal result = strategy.calculateDiscount(
                new BigDecimal("100.00"), List.of(), context(new BigDecimal("20")));
        assertThat(result).isEqualByComparingTo("20.00");
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("0 base => 0 discount")
    void zeroBase() {
        final BigDecimal result = strategy.calculateDiscount(
                BigDecimal.ZERO, List.of(), context(new BigDecimal("40")));
        assertThat(result).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("maxDiscountAmount caps the discount")
    void respectsMaxCap() {
        final BigDecimal result = strategy.calculateDiscount(
                new BigDecimal("1000.00"), List.of(),
                contextWithCap(new BigDecimal("50"), new BigDecimal("100.00")));
        assertThat(result).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("null percentage => IllegalStateException")
    void nullPercentageThrows() {
        assertThatThrownBy(() -> strategy.calculateDiscount(
                new BigDecimal("100.00"), List.of(), context(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-null percentage");
    }

    @ParameterizedTest(name = "[{index}] base={0}, percent={1} -> {2}")
    @CsvSource({
            "10.00, 10, 1.00",
            "25.00, 20, 5.00",
            "99.99, 40, 40.00",
            "1.005, 40, 0.40",
            "1000000000, 40, 400000000.00"
    })
    void parameterised(final String base, final String pct, final String expected) {
        final BigDecimal result = strategy.calculateDiscount(
                new BigDecimal(base), List.of(), context(new BigDecimal(pct)));
        assertThat(result).isEqualByComparingTo(expected);
        assertThat(result.scale()).isEqualTo(2);
    }
}
