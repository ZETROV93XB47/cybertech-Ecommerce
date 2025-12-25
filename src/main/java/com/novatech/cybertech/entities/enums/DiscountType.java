package com.novatech.cybertech.entities.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DiscountType {
    BUY_ONE_GET_ONE_FREE(1f),
    BLACK_FRIDAY(0.4f),
    WINTER_SALES(0.2f),
    SPRING_SALES(0.3f),
    NO_DISCOUNT(1f);

    private final float discountPercentage;
}