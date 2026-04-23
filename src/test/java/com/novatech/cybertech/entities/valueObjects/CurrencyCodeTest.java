package com.novatech.cybertech.entities.valueObjects;

import org.junit.jupiter.api.Disabled;
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
        void enumHasExactlyTenCurrenciesToday() {
            // Pin: USD/EUR/GBP/JPY/AUD/CAD/CHF/CNY/SEK/NZD.
            assertThat(CurrencyCode.values()).hasSize(10);
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
        @Disabled("BUG-133 — CurrencyCode missing INR/BRL/MXN/RUB/KRW/ZAR")
        void supportsCommonEmergingMarketCurrencies_BUG_133() {
            final List<String> names = Arrays.stream(CurrencyCode.values()).map(Enum::name).toList();

            assertThat(names).contains("INR", "BRL", "MXN", "RUB", "KRW", "ZAR");
        }

        @Test
        void documentsCurrentMissingCurrencies_BUG_133() {
            final List<String> names = Arrays.stream(CurrencyCode.values()).map(Enum::name).toList();

            // Pin: today these are NOT present.
            assertThat(names).doesNotContain("INR", "BRL", "MXN", "RUB", "KRW", "ZAR");
        }
    }

    @Nested
    @DisplayName("API surface")
    class ApiSurface {

        @Test
        @Disabled("BUG-134 — brief asks for fromString(String); production exposes only fromCode(String)")
        void shouldExposeFromStringFactory_BUG_134() throws NoSuchMethodException {
            final Method fromString = CurrencyCode.class.getMethod("fromString", String.class);

            assertThat(fromString).isNotNull();
        }

        @Test
        void documentsCurrentlyOnlyFromCodeIsExposed_BUG_134() {
            final List<String> staticFactoryNames = Arrays.stream(CurrencyCode.class.getDeclaredMethods())
                    .map(Method::getName)
                    .filter(n -> n.startsWith("from"))
                    .toList();

            assertThat(staticFactoryNames).contains("fromCode");
            assertThat(staticFactoryNames).doesNotContain("fromString");
        }
    }
}
