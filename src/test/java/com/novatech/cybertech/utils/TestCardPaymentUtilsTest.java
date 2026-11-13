package com.novatech.cybertech.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestCardPaymentUtilsTest {

    @ParameterizedTest
    @ValueSource(strings = {"1111111111111111", "1111111111111", "1111111111111111111"})
    void onlyOnesPays(final String cardNumber) {
        assertThat(TestCardPaymentUtils.isPayingCard(cardNumber)).isTrue();
        assertThat(TestCardPaymentUtils.stripeTestPaymentMethodFor(cardNumber)).isEqualTo("pm_card_visa");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0000000000000000", "4242424242424242", "1111111111111110", "2111111111111111"})
    void anyOtherNumberIsDeclined(final String cardNumber) {
        assertThat(TestCardPaymentUtils.isPayingCard(cardNumber)).isFalse();
        assertThat(TestCardPaymentUtils.stripeTestPaymentMethodFor(cardNumber)).isEqualTo("pm_card_chargeDeclined");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void missingNumberIsDeclined(final String cardNumber) {
        assertThat(TestCardPaymentUtils.isPayingCard(cardNumber)).isFalse();
    }
}
