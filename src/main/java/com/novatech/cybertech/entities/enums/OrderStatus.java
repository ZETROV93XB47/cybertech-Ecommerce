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
    SHIPPED(5),
    DELIVERED(6),
    RETURNED(7),
    CANCELED(8),
    REFUNDED(9);

    private final Integer code;
}