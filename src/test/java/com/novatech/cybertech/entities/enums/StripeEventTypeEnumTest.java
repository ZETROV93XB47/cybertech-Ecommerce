package com.novatech.cybertech.entities.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class StripeEventTypeEnumTest {

    @Test
    void hasFiveEvents() {
        assertThat(StripeEventType.values()).containsExactly(
                StripeEventType.PAYMENT_INTENT_SUCCEEDED,
                StripeEventType.PAYMENT_INTENT_PAYMENT_FAILED,
                StripeEventType.CHECKOUT_SESSION_COMPLETED,
                StripeEventType.CHARGE_REFUNDED,
                StripeEventType.UNKNOWN);
    }

    @ParameterizedTest
    @EnumSource(StripeEventType.class)
    void fromValueRoundTripsEveryConstant(final StripeEventType event) {
        assertThat(StripeEventType.fromValue(event.getValue())).isSameAs(event);
    }

    @ParameterizedTest
    @EnumSource(StripeEventType.class)
    void fromRoundTripsEveryConstant(final StripeEventType event) {
        assertThat(StripeEventType.from(event.getValue())).isSameAs(event);
    }

    @Test
    void fromNullReturnsUnknown() {
        // Documented short-circuit: null -> UNKNOWN.
        assertThat(StripeEventType.from(null)).isSameAs(StripeEventType.UNKNOWN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown.event", "", "  ", "PAYMENT_INTENT.SUCCEEDED"})
    void fromUnknownReturnsUnknown(final String raw) {
        assertThat(StripeEventType.from(raw)).isSameAs(StripeEventType.UNKNOWN);
    }

    @Test
    void fromValueWithNullReturnsUnknown() {
        // Used by Jackson via @JsonCreator. Stream filter on `v.getValue().equals(null)` is always false.
        assertThat(StripeEventType.fromValue(null)).isSameAs(StripeEventType.UNKNOWN);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"foo", "payment_intent.success" /* missing 'ed' */})
    void fromValueUnknownReturnsUnknown(final String value) {
        assertThat(StripeEventType.fromValue(value)).isSameAs(StripeEventType.UNKNOWN);
    }

    @Test
    void canonicalRawValueMapping() {
        // Pin Stripe's exact wire-format strings — drift breaks webhook routing.
        assertThat(StripeEventType.PAYMENT_INTENT_SUCCEEDED.getValue()).isEqualTo("payment_intent.succeeded");
        assertThat(StripeEventType.PAYMENT_INTENT_PAYMENT_FAILED.getValue()).isEqualTo("payment_intent.payment_failed");
        assertThat(StripeEventType.CHECKOUT_SESSION_COMPLETED.getValue()).isEqualTo("checkout.session.completed");
        assertThat(StripeEventType.CHARGE_REFUNDED.getValue()).isEqualTo("charge.refunded");
        assertThat(StripeEventType.UNKNOWN.getValue()).isEqualTo("unknown");
    }
}
