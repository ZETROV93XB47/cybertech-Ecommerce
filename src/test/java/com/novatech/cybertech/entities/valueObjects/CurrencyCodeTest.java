package com.novatech.cybertech.entities.valueObjects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CurrencyCode}. Pure enum; no Spring/Mockito.
 *
 * Pinned bugs (carried over from SA4.5R wave — DO NOT renumber):
 *   - BUG-133: enum lacks INR/BRL/MXN/RUB/KRW/ZAR.
 *   - BUG-134: no fromString(String) — naming-convention mismatch with brief.
 */
class CurrencyCodeTest {

    @Nested
    @DisplayName("getCode round-trip")
    class CodeRoundTrip {

        @ParameterizedTest
        @EnumSource(CurrencyCode.class)
        void everyConstantRoundTripsThroughFromCode(final CurrencyCode value) {
            assertThat(CurrencyCode.fromCode(value.getCode())).isSameAs(value);
        }

        @ParameterizedTest
        @EnumSource(CurrencyCode.class)
        void getCodeMatchesEnumName(final CurrencyCode value) {
            assertThat(value.getCode()).isEqualTo(value.name());
        }
    }

    @Nested
    @DisplayName("fromCode(...)")
    class FromCode {

        @Test
        void happyPathReturnsMatchingConstant() {
            assertThat(CurrencyCode.fromCode("EUR")).isSameAs(CurrencyCode.EUR);
            assertThat(CurrencyCode.fromCode("USD")).isSameAs(CurrencyCode.USD);
        }

        @ParameterizedTest
        @ValueSource(strings = {"eur", "Eur", "EUR", "eUr"})
        void isCaseInsensitive(final String input) {
            assertThat(CurrencyCode.fromCode(input)).isSameAs(CurrencyCode.EUR);
        }

        @Test
        void unknownCodeThrowsIllegalArgumentException() {
            assertThatThrownBy(() -> CurrencyCode.fromCode("XXX"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid currency code")
                    .hasMessageContaining("XXX");
        }

        @Test
        void nullCodeThrowsIllegalArgumentException() {
            // The internal lambda calls c.getCode().equalsIgnoreCase(null) -> false for every entry,
            // so the stream's orElseThrow fires. Pin current behaviour.
            assertThatThrownBy(() -> CurrencyCode.fromCode(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid currency code");
        }
    }

    @Nested
    @DisplayName("Coverage of supported currencies")
    class CurrencyCoverage {

        @Test
        void enumHasExactlySixteenCurrencies_pinsFix_BUG_133() {
            // Pin the FIX: 10 historical (USD/EUR/GBP/JPY/AUD/CAD/CHF/CNY/SEK/NZD)
            // + 6 emerging-market additions (INR/BRL/MXN/RUB/KRW/ZAR).
            assertThat(CurrencyCode.values()).hasSize(16);
        }

        @Test
        void containsCoreMajorCurrencies() {
            assertThat(CurrencyCode.values())
                    .contains(
                            CurrencyCode.USD,
                            CurrencyCode.EUR,
                            CurrencyCode.GBP,
                            CurrencyCode.JPY,
                            CurrencyCode.AUD,
                            CurrencyCode.CAD,
                            CurrencyCode.CHF,
                            CurrencyCode.CNY,
                            CurrencyCode.SEK,
                            CurrencyCode.NZD);
        }

        @Test
        void supportsCommonEmergingMarketCurrencies_BUG_133() {
            final List<String> names = Arrays.stream(CurrencyCode.values()).map(Enum::name).toList();

            assertThat(names).contains("INR", "BRL", "MXN", "RUB", "KRW", "ZAR");
        }

        @Test
        void documentsCurrentEmergingMarketCurrencies_pinsFix_BUG_133() {
            final List<String> names = Arrays.stream(CurrencyCode.values()).map(Enum::name).toList();

            // Pin the FIX: emerging-market currencies are now present.
            assertThat(names).contains("INR", "BRL", "MXN", "RUB", "KRW", "ZAR");
        }
    }

    @Nested
    @DisplayName("API surface")
    class ApiSurface {

        @Test
        void shouldExposeFromStringFactory_BUG_134() throws NoSuchMethodException {
            final Method fromString = CurrencyCode.class.getMethod("fromString", String.class);

            assertThat(fromString).isNotNull();
        }

        @Test
        void fromStringDelegatesToFromCode_BUG_134() {
            assertThat(CurrencyCode.fromString("eur")).isSameAs(CurrencyCode.EUR);
            assertThat(CurrencyCode.fromString("USD")).isSameAs(CurrencyCode.USD);
        }

        @Test
        void documentsBothFactoriesAreExposed_pinsFix_BUG_134() {
            final List<String> staticFactoryNames = Arrays.stream(CurrencyCode.class.getDeclaredMethods())
                    .map(Method::getName)
                    .filter(n -> n.startsWith("from"))
                    .toList();

            // Pin the FIX: both fromCode AND fromString are now exposed.
            assertThat(staticFactoryNames).contains("fromCode", "fromString");
        }
    }
}
