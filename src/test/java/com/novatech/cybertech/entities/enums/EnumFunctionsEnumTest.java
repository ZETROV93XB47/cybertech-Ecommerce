package com.novatech.cybertech.entities.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the static {@link EnumFunctions#getByCode(Number, Class)} helper used to look up
 * enum constants by their numeric code. {@code PaymentAttemptStatus} is the only production
 * implementor today; tests double-down on the helper's null- and miss-paths.
 */
class EnumFunctionsEnumTest {

    @Test
    void getByCodeFindsMatchingConstant() {
        assertThat(EnumFunctions.getByCode(2, PaymentAttemptStatus.class))
                .isEqualTo(PaymentAttemptStatus.PROCESSING);
    }

    @Test
    void getByCodeWithNullCodeThrowsIllegalArgumentExceptionMentioningEnumClass() {
        assertThatThrownBy(() -> EnumFunctions.getByCode(null, PaymentAttemptStatus.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code cannot be null")
                .hasMessageContaining(PaymentAttemptStatus.class.getName());
    }

    @Test
    void getByCodeWithUnknownCodeThrowsIllegalArgumentExceptionMentioningCode() {
        assertThatThrownBy(() -> EnumFunctions.getByCode(404, PaymentAttemptStatus.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("404");
    }
}
