package com.novatech.cybertech.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UuidFormatterTest {

    @Nested
    @DisplayName("formatUuidString – happy paths")
    class HappyPath {

        @Test
        @DisplayName("Should format an upper-case 32-char hex string into canonical UUID")
        void shouldFormatUpperCaseHex() {
            // Given
            final String unformatted = "C72E24D08AAF41D3ABF9BD1775E8DD16";

            // When
            final String formatted = UuidFormatter.formatUuidString(unformatted);

            // Then
            assertThat(formatted).isEqualTo("C72E24D0-8AAF-41D3-ABF9-BD1775E8DD16");
            assertThat(UUID.fromString(formatted)).isEqualTo(
                    UUID.fromString("C72E24D0-8AAF-41D3-ABF9-BD1775E8DD16"));
        }

        @Test
        @DisplayName("Should format a lower-case 32-char hex string into canonical UUID")
        void shouldFormatLowerCaseHex() {
            // Given
            final String unformatted = "c72e24d08aaf41d3abf9bd1775e8dd16";

            // When
            final String formatted = UuidFormatter.formatUuidString(unformatted);

            // Then
            assertThat(formatted).isEqualTo("c72e24d0-8aaf-41d3-abf9-bd1775e8dd16");
            assertThat(UUID.fromString(formatted))
                    .isEqualTo(UUID.fromString("c72e24d0-8aaf-41d3-abf9-bd1775e8dd16"));
        }

        @Test
        @DisplayName("Round-trip: formatted string parses back to a valid UUID")
        void shouldRoundTripToUuid() {
            // Given – take a known UUID, strip dashes, refeed into formatter
            final UUID uuid = UUID.randomUUID();
            final String stripped = uuid.toString().replace("-", "");

            // When
            final String formatted = UuidFormatter.formatUuidString(stripped);

            // Then
            assertThat(UUID.fromString(formatted)).isEqualTo(uuid);
        }

        @Test
        @DisplayName("Should preserve the case of input characters in output groups")
        void shouldPreserveCase() {
            // Given – mixed case
            final String unformatted = "AbCdEf01234567890123456789AbCdEf";

            // When
            final String formatted = UuidFormatter.formatUuidString(unformatted);

            // Then – formatter does not toUpperCase or toLowerCase
            assertThat(formatted).isEqualTo("AbCdEf01-2345-6789-0123-456789AbCdEf");
        }
    }

    @Nested
    @DisplayName("formatUuidString – malformed input")
    class MalformedInput {

        @ParameterizedTest
        @NullSource
        @DisplayName("Null input should throw IllegalArgumentException with French message")
        void nullInputThrowsIllegalArgument(String input) {
            assertThatThrownBy(() -> UuidFormatter.formatUuidString(input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("32 caractères");
        }

        @ParameterizedTest(name = "length-mismatch input \"{0}\" -> IllegalArgumentException")
        @ValueSource(strings = {
                "",                                       // 0 chars
                "C72E24D0",                               // 8 chars
                "C72E24D08AAF41D3ABF9BD1775E8DD16AA",     // 34 chars
                "C72E24D08AAF41D3ABF9BD1775E8DD1"         // 31 chars
        })
        @DisplayName("Wrong-length input throws IllegalArgumentException about 32 caractères")
        void wrongLengthThrows(String input) {
            assertThatThrownBy(() -> UuidFormatter.formatUuidString(input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("32 caractères");
        }

        @ParameterizedTest(name = "non-hex input \"{0}\" -> IllegalArgumentException")
        @ValueSource(strings = {
                "GGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGG",       // all G's
                "C72E24D08AAF41D3ABF9BD1775E8DDXY",       // trailing XY
                "                                ",       // 32 spaces
                "0123456789ABCDEF0123456789ABCDE!"        // trailing punctuation
        })
        @DisplayName("Non-hex 32-char input throws IllegalArgumentException about caractères non hexadécimaux")
        void nonHexThrows(String input) {
            assertThatThrownBy(() -> UuidFormatter.formatUuidString(input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("non hexadécimaux");
        }
    }
}
