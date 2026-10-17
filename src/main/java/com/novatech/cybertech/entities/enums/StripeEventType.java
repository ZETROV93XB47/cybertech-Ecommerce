package com.novatech.cybertech.entities.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.Arrays;

@Getter
@ToString
@RequiredArgsConstructor
public enum StripeEventType {

    PAYMENT_INTENT_SUCCEEDED("payment_intent.succeeded"),
    PAYMENT_INTENT_PAYMENT_FAILED("payment_intent.payment_failed"),
    CHECKOUT_SESSION_COMPLETED("checkout.session.completed"),
    CHARGE_REFUNDED("charge.refunded"),
    UNKNOWN("unknown");

    private final String value;

    public static StripeEventType from(String raw) {

        if (raw == null) return UNKNOWN;

        return Arrays.stream(values())
                .filter(v -> v.value.equals(raw))
                .findFirst().orElse(UNKNOWN);
    }

    @JsonCreator
    public static StripeEventType fromValue(String value) {
        return Arrays.stream(values()).filter(v -> v.getValue().equals(value)).findFirst().orElse(UNKNOWN);
    }
}