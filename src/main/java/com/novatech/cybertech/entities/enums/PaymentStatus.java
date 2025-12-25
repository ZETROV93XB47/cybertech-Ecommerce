package com.novatech.cybertech.entities.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentStatus implements EnumFunctions<Integer> {
    //PENDING(1),
    SUCCESS(1),
    FAILED(2);

    private final Integer code;

    public static PaymentStatus getByCode(final Integer code) {
        return EnumFunctions.getByCode(code, PaymentStatus.class);
    }
}
