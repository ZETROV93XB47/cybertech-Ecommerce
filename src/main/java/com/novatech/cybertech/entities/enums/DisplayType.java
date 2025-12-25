package com.novatech.cybertech.entities.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static java.util.Arrays.stream;

@Getter
@RequiredArgsConstructor
public enum DisplayType {

    VA(1),
    LCD(2),
    PVA(3),
    AMVA(4),
    AMVA_PLUS(5),
    IPS(6),
    TN(7);

    private final Integer code;

    public static DisplayType getByCode(int code) {
        return stream(values()).filter(display -> display.getCode() == code).findFirst().orElseThrow(IllegalArgumentException::new);
    }
}
