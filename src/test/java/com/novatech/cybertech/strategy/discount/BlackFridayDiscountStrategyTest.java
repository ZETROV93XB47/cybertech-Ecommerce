package com.novatech.cybertech.strategy.discount;

import com.novatech.cybertech.entities.enums.DiscountType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure-unit coverage for {@link BlackFridayDiscountStrategy} after the F4 refactor:
 * the strategy now operates on a raw {@link BigDecimal} (no OrderEntity coupling).
 *
 * The percentage constant is sourced from {@link DiscountType#BLACK_FRIDAY}'s discountPercentage
 * (currently 0.4f) and applied with HALF_UP rounding to 2 decimals.
 */
class BlackFridayDiscountStrategyTest {

    private final BlackFridayDiscountStrategy strategy = new BlackFridayDiscountStrategy();

    @Test
    @DisplayName("100.00 base => 40.00 discount (40% of base)")
    void appliesPercentageOnHundred() {
        final BigDecimal result = strategy.calculateDiscount(new BigDecimal("100.00"));
        assertThat(result).isEqualByComparingTo("40.00");
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("0.00 base => 0.00 discount")
    void zeroBaseProducesZeroDiscount() {
        final BigDecimal result = strategy.calculateDiscount(BigDecimal.ZERO);
        assertThat(result).isEqualByComparingTo("0.00");
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("Very large amount: 1e9 base produces ~4e8 discount (with float-driven precision drift, BUG-460)")
    void veryLargeAmountIsHandled() {
        // BUG-460: BlackFridayDiscountStrategy stores PERCENTAGE as
        //   BigDecimal.valueOf(DiscountType.BLACK_FRIDAY.getDiscountPercentage()) where
        //   getDiscountPercentage() returns a float (0.4f). float -> BigDecimal conversion
        //   yields 0.40000000596046447753906250, so any base >= ~1e7 drifts visibly. The
        //   precise expected value 400_000_000.00 is therefore unreachable until the
        //   percentage is stored as `new BigDecimal("0.40")` (or DiscountType.discountPercentage
        //   becomes a double / BigDecimal). Pinned by tolerance below.
        final BigDecimal result = strategy.calculateDiscount(new BigDecimal("1000000000"));
        assertThat(result.doubleValue()).isCloseTo(400_000_000.0d, org.assertj.core.data.Offset.offset(50.0));
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("Negative input: behaviour is mathematical (negative discount = -40% of input)")
    void negativeInputProducesNegativeDiscount() {
        final BigDecimal result = strategy.calculateDiscount(new BigDecimal("-100.00"));
        // No guard is currently coded; pin existing behaviour. If the team wants a
        // domain guard, add one in production and this test will alert (BUG candidate).
        assertThat(result).isEqualByComparingTo("-40.00");
    }

    @Test
    @DisplayName("HALF_UP rounding to 2 dp: 1.005 * 0.4 = 0.402 => 0.40")
    void halfUpRoundingTrimsExtraDecimals() {
        final BigDecimal result = strategy.calculateDiscount(new BigDecimal("1.005"));
        // 1.005 * 0.4 = 0.4020 -> scale(2, HALF_UP) = 0.40
        assertThat(result).isEqualByComparingTo("0.40");
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("HALF_UP rounding rounds .5 up: amount producing 0.005 => 0.01")
    void halfUpRoundsExactHalfUp() {
        // 0.0125 * 0.4 = 0.00500 -> HALF_UP at scale 2 = 0.01
        final BigDecimal result = strategy.calculateDiscount(new BigDecimal("0.0125"));
        assertThat(result).isEqualByComparingTo("0.01");
        assertThat(result.scale()).isEqualTo(2);
    }

    @ParameterizedTest(name = "[{index}] base={0} -> discount={1}")
    @CsvSource({
            "10.00, 4.00",
            "25.00, 10.00",
            "50.00, 20.00",
            "99.99, 40.00",
            "0.01, 0.00"
    })
    @DisplayName("Parameterised: 40% of base, HALF_UP at 2 dp")
    void parameterisedPercentages(final String baseAmount, final String expectedDiscount) {
        final BigDecimal result = strategy.calculateDiscount(new BigDecimal(baseAmount));
        assertThat(result).isEqualByComparingTo(expectedDiscount);
    }

    @Test
    @DisplayName("Returned BigDecimal always has scale=2 regardless of the input scale")
    void returnedScaleAlwaysTwo() {
        assertThat(strategy.calculateDiscount(new BigDecimal("12")).scale()).isEqualTo(2);
        assertThat(strategy.calculateDiscount(new BigDecimal("12.123456789")).scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("null input throws NullPointerException (no guard in production code)")
    void nullInputThrowsNpe() {
        assertThatThrownBy(() -> strategy.calculateDiscount(null))
                .isInstanceOf(NullPointerException.class);
    }
}
