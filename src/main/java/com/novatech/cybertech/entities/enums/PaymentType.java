package com.novatech.cybertech.entities.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static com.novatech.cybertech.entities.enums.PaymentServiceProvider.STRIPE;

@Getter
@RequiredArgsConstructor
public enum PaymentType {

    MASTERCARD(1, STRIPE),
    VISA(2, STRIPE),
    APPLE_PAY(3, STRIPE),
    GOOGLE_PAY(4, STRIPE);

    private final Integer code;
    private final PaymentServiceProvider paymentServiceProvider;
}
