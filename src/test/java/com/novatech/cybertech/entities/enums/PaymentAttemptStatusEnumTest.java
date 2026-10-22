package com.novatech.cybertech.entities.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentAttemptStatusEnumTest {

    @Test
    void hasSixStatuses() {
        assertThat(PaymentAttemptStatus.values()).hasSize(6);
    }

    @ParameterizedTest
    @EnumSource(PaymentAttemptStatus.class)
    void everyStatusHasNonNullCode(final PaymentAttemptStatus status) {
        assertThat(status.getCode()).isNotNull().isPositive();
    }

    @Test
    void codesAreContiguousFromOne() {
        assertThat(Arrays.stream(PaymentAttemptStatus.values()).map(PaymentAttemptStatus::getCode).sorted().toList())
                .containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    void implementsEnumFunctionsByCodeLookup() {
        // PaymentAttemptStatus is the only enum implementing EnumFunctions<Integer> — exercise both directions.
        assertThat(EnumFunctions.getByCode(4, PaymentAttemptStatus.class))
                .isEqualTo(PaymentAttemptStatus.SUCCESS);
        assertThat(EnumFunctions.getByCode(1, PaymentAttemptStatus.class))
                .isEqualTo(PaymentAttemptStatus.CREATED);
    }

    @Test
    void getByCodeWithNullThrows() {
        assertThatThrownBy(() -> EnumFunctions.getByCode(null, PaymentAttemptStatus.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code cannot be null");
    }

    @Test
    void getByCodeWithUnknownCodeThrows() {
        assertThatThrownBy(() -> EnumFunctions.getByCode(999, PaymentAttemptStatus.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No Enum Constant with code");
    }
}
