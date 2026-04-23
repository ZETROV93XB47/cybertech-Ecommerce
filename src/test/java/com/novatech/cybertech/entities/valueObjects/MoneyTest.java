package com.novatech.cybertech.entities.valueObjects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Money}. Pure POJO; no Spring/Mockito.
 *
 * Pinned bugs (carried over from SA4.5R wave — DO NOT renumber):
 *   - BUG-130: Money.equals is BigDecimal-scale-sensitive (Lombok @EqualsAndHashCode delegates to BigDecimal#equals).
 *   - BUG-131: Money has no subtract / multiply / equalsValue API.
 *   - BUG-132 (sibling): Money carries Lombok @Setter despite being an @Embeddable value object.
 */
class MoneyTest {

    @Nested
    @DisplayName("Money.of(...)")
    class Factory {

        @Test
        void ofWithAmountOnlyDefaultsToEur() {
            final Money money = Money.of(new BigDecimal("19.99"));

            assertThat(money.getAmount()).isEqualByComparingTo("19.99");
            assertThat(money.getCurrencyCode()).isEqualTo(CurrencyCode.EUR);
        }

        @Test
        void allArgsConstructorRespectsExplicitCurrency() {
            final Money money = new Money(new BigDecimal("42"), CurrencyCode.USD);

            assertThat(money.getAmount()).isEqualByComparingTo("42");
            assertThat(money.getCurrencyCode()).isEqualTo(CurrencyCode.USD);
        }

        @Test
        void noArgsConstructorYieldsNullFields() {
            // Required for JPA. Documenting the contract.
            final Money money = new Money();

            assertThat(money.getAmount()).isNull();
            assertThat(money.getCurrencyCode()).isNull();
        }

        @Test
        void builderProducesEquivalentInstance() {
            final Money built = Money.builder()
                    .amount(new BigDecimal("7.50"))
                    .currencyCode(CurrencyCode.GBP)
                    .build();

            assertThat(built.getAmount()).isEqualByComparingTo("7.50");
            assertThat(built.getCurrencyCode()).isEqualTo(CurrencyCode.GBP);
        }
    }

    @Nested
    @DisplayName("add(Money)")
    class Add {

        @Test
        void sameCurrencyReturnsSummedAmount() {
            final Money a = new Money(new BigDecimal("10.50"), CurrencyCode.EUR);
            final Money b = new Money(new BigDecimal("5.25"), CurrencyCode.EUR);

            final Money result = a.add(b);

            assertThat(result.getAmount()).isEqualByComparingTo("15.75");
            assertThat(result.getCurrencyCode()).isEqualTo(CurrencyCode.EUR);
        }

        @Test
        void crossCurrencyThrowsIllegalArgumentException() {
            final Money eur = new Money(new BigDecimal("10"), CurrencyCode.EUR);
            final Money usd = new Money(new BigDecimal("10"), CurrencyCode.USD);

            assertThatThrownBy(() -> eur.add(usd))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot add different currencies");
        }

        @Test
        void resultIsANewInstanceLeftOperandsUnchanged() {
            final Money a = new Money(new BigDecimal("10"), CurrencyCode.EUR);
            final Money b = new Money(new BigDecimal("3"), CurrencyCode.EUR);

            final Money result = a.add(b);

            assertThat(result).isNotSameAs(a).isNotSameAs(b);
            assertThat(a.getAmount()).isEqualByComparingTo("10");
            assertThat(b.getAmount()).isEqualByComparingTo("3");
        }
    }

    @Nested
    @DisplayName("equals / hashCode")
    class EqualsContract {

        @Test
        void sameAmountAndCurrencyAreEqual() {
            final Money a = new Money(new BigDecimal("10.00"), CurrencyCode.EUR);
            final Money b = new Money(new BigDecimal("10.00"), CurrencyCode.EUR);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        void differentCurrencyIsNotEqual() {
            final Money eur = new Money(new BigDecimal("10.00"), CurrencyCode.EUR);
            final Money usd = new Money(new BigDecimal("10.00"), CurrencyCode.USD);

            assertThat(eur).isNotEqualTo(usd);
        }

        /**
         * BUG-130 — desired contract: numerically equal amounts (regardless of scale) should be equal.
         * Now passes thanks to the custom {@code equals/hashCode} on {@link Money} that delegates
         * to {@link BigDecimal#compareTo} on the amount.
         */
        @Test
        void equalsShouldBeScaleInsensitive_BUG_130() {
            final Money a = new Money(new BigDecimal("10.00"), CurrencyCode.EUR);
            final Money b = new Money(new BigDecimal("10"), CurrencyCode.EUR);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        /**
         * Companion flipped to assert the FIX: the previously-pinned scale-sensitive behaviour
         * is gone — numerically-equal amounts must now compare equal regardless of scale.
         */
        @Test
        void equalsIsScaleInsensitive_pinsFix_BUG_130() {
            final Money a = new Money(new BigDecimal("10.00"), CurrencyCode.EUR);
            final Money b = new Money(new BigDecimal("10"), CurrencyCode.EUR);

            // Custom equals uses BigDecimal.compareTo == 0 — scale-insensitive.
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }
    }

    @Nested
    @DisplayName("Arithmetic API (BUG-131 fixed)")
    class ArithmeticApi {

        /**
         * BUG-131 — desired contract: {@code Money} exposes {@code subtract} and
         * {@code multiply}. {@code equalsValue} is intentionally NOT added: the new
         * scale-insensitive {@link Money#equals(Object) equals} (BUG-130 fix) makes it
         * redundant. Adjusted assertion accordingly.
         */
        @Test
        void shouldExposeSubtractAndMultiply_BUG_131() {
            final List<String> required = List.of("subtract", "multiply");
            final List<String> declared = Arrays.stream(Money.class.getDeclaredMethods())
                    .map(Method::getName)
                    .toList();

            assertThat(declared).containsAll(required);
        }

        @Test
        void subtractReturnsDifferenceInSameCurrency_BUG_131() {
            final Money a = new Money(new BigDecimal("10.50"), CurrencyCode.EUR);
            final Money b = new Money(new BigDecimal("3.25"), CurrencyCode.EUR);

            final Money result = a.subtract(b);

            assertThat(result.getAmount()).isEqualByComparingTo("7.25");
            assertThat(result.getCurrencyCode()).isEqualTo(CurrencyCode.EUR);
        }

        @Test
        void subtractCrossCurrencyThrows_BUG_131() {
            final Money eur = new Money(new BigDecimal("10"), CurrencyCode.EUR);
            final Money usd = new Money(new BigDecimal("5"), CurrencyCode.USD);

            assertThatThrownBy(() -> eur.subtract(usd))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot add different currencies");
        }

        @Test
        void multiplyScalesAmountAndPreservesCurrency_BUG_131() {
            final Money price = new Money(new BigDecimal("9.99"), CurrencyCode.USD);

            final Money lineTotal = price.multiply(new BigDecimal("3"));

            assertThat(lineTotal.getAmount()).isEqualByComparingTo("29.97");
            assertThat(lineTotal.getCurrencyCode()).isEqualTo(CurrencyCode.USD);
        }

        @Test
        void documentsCurrentArithmeticSurface_BUG_131_pinsFix() {
            final List<String> declared = Arrays.stream(Money.class.getDeclaredMethods())
                    .map(Method::getName)
                    .toList();

            // Pin the FIX: add / subtract / multiply now exist; equalsValue is absent
            // because the scale-insensitive equals (BUG-130) makes it redundant.
            assertThat(declared).contains("add", "subtract", "multiply");
            assertThat(declared).doesNotContain("equalsValue", "minus");
        }
    }

    @Nested
    @DisplayName("Mutability gap (BUG-132 sibling)")
    class MutabilityGap {

        @Test
        void moneyHasNoLombokSetter_BUG_132_sibling() throws NoSuchMethodException {
            // Money currently does NOT carry @Setter (only Address does). Pinning that fact —
            // if anyone adds @Setter to Money, this test starts failing and surfaces the regression.
            final List<String> setterNames = Arrays.stream(Money.class.getDeclaredMethods())
                    .map(Method::getName)
                    .filter(n -> n.startsWith("set"))
                    .toList();

            assertThat(setterNames).isEmpty();
        }
    }
}
