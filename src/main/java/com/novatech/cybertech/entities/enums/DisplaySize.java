package com.novatech.cybertech.entities.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static java.util.Arrays.stream;

@Getter
@RequiredArgsConstructor
public enum DisplaySize {

    _10_INCHES(1, 10),
    _11_INCHES(2, 11),
    _12_INCHES(3, 12),
    _13_INCHES(4, 13),
    _14_INCHES(5, 14),
    _15_INCHES(6, 15);

    private final int code;
    private final int size;

    public static DisplaySize getByCode(int code) {
        return stream(values()).filter(display -> display.getCode() == code).findFirst().orElseThrow(IllegalArgumentException::new);
    }
}
