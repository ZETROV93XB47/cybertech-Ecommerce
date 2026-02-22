package com.novatech.cybertech.entities.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentAttemptStatus implements EnumFunctions<Integer> {
    CREATED(1),
    PROCESSING(2),
    FAILED(3),
    SUCCESS(4),
    CANCELED(5);

    private final Integer code;
}
