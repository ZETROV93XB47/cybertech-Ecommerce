package com.novatech.cybertech.entities.valueObjects;

import org.junit.jupiter.api.Disabled;
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
         * Pinned @Disabled — will flip green once Money overrides equals to use BigDecimal#compareTo.
         */
        @Test
        @Disabled("BUG-130 — Money.equals is BigDecimal-scale-sensitive; should use compareTo == 0")
        void equalsShouldBeScaleInsensitive_BUG_130() {
            final Money a = new Money(new BigDecimal("10.00"), CurrencyCode.EUR);
            final Money b = new Money(new BigDecimal("10"), CurrencyCode.EUR);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        /**
         * Companion that pins the CURRENT (broken) behaviour. Will start failing the moment
         * BUG-130 is fixed — that's intentional, it forces a coordinated update.
         */
        @Test
        void equalsIsScaleSensitive_pinsCurrentBehaviour_BUG_130() {
            final Money a = new Money(new BigDecimal("10.00"), CurrencyCode.EUR);
            final Money b = new Money(new BigDecimal("10"), CurrencyCode.EUR);

            // Lombok-generated equals uses BigDecimal.equals which IS scale-sensitive.
            assertThat(a).isNotEqualTo(b);
        }
    }

    @Nested
    @DisplayName("Missing arithmetic API (BUG-131)")
    class MissingArithmeticApi {

        @Test
        @Disabled("BUG-131 — Money missing subtract / multiply / equalsValue API")
        void shouldExposeSubtractMultiplyAndEqualsValue_BUG_131() {
            final List<String> required = List.of("subtract", "multiply", "equalsValue");
            final List<String> declared = Arrays.stream(Money.class.getDeclaredMethods())
                    .map(Method::getName)
                    .toList();

            assertThat(declared).containsAll(required);
        }

        @Test
        void documentsCurrentlyMissingArithmeticSurface_BUG_131() {
            final List<String> declared = Arrays.stream(Money.class.getDeclaredMethods())
                    .map(Method::getName)
                    .toList();

            // Pin: today, only `add` exists — none of the others.
            assertThat(declared).contains("add");
            assertThat(declared).doesNotContain("subtract", "multiply", "equalsValue", "minus");
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
