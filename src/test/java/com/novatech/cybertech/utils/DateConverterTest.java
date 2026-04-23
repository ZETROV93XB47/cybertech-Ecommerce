package com.novatech.cybertech.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DateConverterTest {

    @Nested
    @DisplayName("convertExpiryDateToLocalDate – happy paths")
    class HappyPath {

        @Test
        @DisplayName("Should return last day of December for 12/2025")
        void shouldReturnLastDayOfDecemberFor12_2025() {
            // Given
            final String input = "12/2025";

            // When
            final LocalDate result = DateConverter.convertExpiryDateToLocalDate(input);

            // Then
            assertThat(result).isEqualTo(LocalDate.of(2025, 12, 31));
        }

        @Test
        @DisplayName("Leap year: should return Feb 29 for 02/2024")
        void shouldReturnFeb29ForLeapYear() {
            // Given
            final String input = "02/2024";

            // When
            final LocalDate result = DateConverter.convertExpiryDateToLocalDate(input);

            // Then
            assertThat(result).isEqualTo(LocalDate.of(2024, 2, 29));
        }

        @Test
        @DisplayName("Non-leap year: should return Feb 28 for 02/2023")
        void shouldReturnFeb28ForNonLeapYear() {
            // Given
            final String input = "02/2023";

            // When
            final LocalDate result = DateConverter.convertExpiryDateToLocalDate(input);

            // Then
            assertThat(result).isEqualTo(LocalDate.of(2023, 2, 28));
        }

        @ParameterizedTest(name = "Should parse {0} -> last day {1}")
        @CsvSource({
                "01/2026, 2026-01-31",
                "04/2026, 2026-04-30",
                "06/2026, 2026-06-30",
                "07/2026, 2026-07-31",
                "11/2030, 2030-11-30"
        })
        @DisplayName("Should parse a range of valid MM/yyyy strings")
        void shouldParseValidStrings(String input, String expectedIso) {
            assertThat(DateConverter.convertExpiryDateToLocalDate(input))
                    .isEqualTo(LocalDate.parse(expectedIso));
        }

        @Test
        @DisplayName("DST boundary in Europe (March 2026) is irrelevant for date-only result")
        void shouldHandleMarch2026DstBoundary() {
            // Given – DST switch in Europe is the last Sunday of March 2026 (March 29)
            // The converter returns a LocalDate, so DST has no effect, but we pin behaviour.
            final String input = "03/2026";

            // When
            final LocalDate result = DateConverter.convertExpiryDateToLocalDate(input);

            // Then
            assertThat(result).isEqualTo(LocalDate.of(2026, 3, 31));
        }
    }

    @Nested
    @DisplayName("convertExpiryDateToLocalDate – edge cases")
    class EdgeCases {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   ", "\t"})
        @DisplayName("Should return null when input is null, empty or blank")
        void shouldReturnNullForNullOrBlank(String input) {
            assertThat(DateConverter.convertExpiryDateToLocalDate(input)).isNull();
        }

        @ParameterizedTest(name = "Malformed input \"{0}\" returns null")
        @ValueSource(strings = {
                "13/2025",      // invalid month
                "00/2025",      // month zero
                "12-2025",      // wrong separator
                "2025/12",      // wrong order
                "12/25",        // 2-digit year
                "abc/defg",     // non-numeric
                "12/2025/extra",// extra segment
                "12"            // missing year
        })
        @DisplayName("Should return null for malformed inputs (current contract)")
        void shouldReturnNullForMalformedInputs(String input) {
            assertThat(DateConverter.convertExpiryDateToLocalDate(input)).isNull();
        }

        @Test
        @DisplayName("Should accept epoch-zero year: 01/0001 -> last day Jan year 1")
        void shouldAcceptYearOne() {
            // Given
            final String input = "01/0001";

            // When
            final LocalDate result = DateConverter.convertExpiryDateToLocalDate(input);

            // Then
            assertThat(result).isEqualTo(LocalDate.of(1, 1, 31));
        }

        @Test
        @DisplayName("Should accept far-future year: 12/9999 -> last day Dec year 9999")
        void shouldAcceptFarFutureYear() {
            // Given
            final String input = "12/9999";

            // When
            final LocalDate result = DateConverter.convertExpiryDateToLocalDate(input);

            // Then
            assertThat(result).isEqualTo(LocalDate.of(9999, 12, 31));
        }
    }
}
