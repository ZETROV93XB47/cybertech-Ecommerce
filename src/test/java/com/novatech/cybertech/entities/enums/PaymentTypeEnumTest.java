package com.novatech.cybertech.entities.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentTypeEnumTest {

    @Test
    void hasExactlyFourPaymentTypes() {
        assertThat(PaymentType.values()).containsExactly(
                PaymentType.MASTERCARD,
                PaymentType.VISA,
                PaymentType.APPLE_PAY,
                PaymentType.GOOGLE_PAY);
    }

    @ParameterizedTest
    @EnumSource(PaymentType.class)
    void allPaymentTypesRouteThroughStripeToday(final PaymentType type) {
        assertThat(type.getPaymentServiceProvider()).isEqualTo(PaymentServiceProvider.STRIPE);
    }

    @Test
    void canonicalCodeMapping() {
        assertThat(PaymentType.MASTERCARD.getCode()).isEqualTo(1);
        assertThat(PaymentType.VISA.getCode()).isEqualTo(2);
        assertThat(PaymentType.APPLE_PAY.getCode()).isEqualTo(3);
        assertThat(PaymentType.GOOGLE_PAY.getCode()).isEqualTo(4);
    }
}
