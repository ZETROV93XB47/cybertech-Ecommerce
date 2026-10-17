package com.novatech.cybertech.entities.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {
    CREATED(1),
    AWAITING_PAYMENT(2),
    PAYMENT_FAILED(3),
    PAID(4),
    AWAITING_SHIPPING(5),
    SHIPPED(6),
    DELIVERED(7),
    RETURNED(8),
    CANCELED(9),
    REFUNDED(10);

    private final Integer code;
}