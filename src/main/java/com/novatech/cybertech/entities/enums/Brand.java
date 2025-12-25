package com.novatech.cybertech.entities.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static java.util.Arrays.stream;

@Getter
@RequiredArgsConstructor
public enum Brand {

    ASUS(1),
    HP(2),
    LENOVO(3),
    DELL(4),
    AORUS(5),
    MSI(6),
    ACER(7),
    TOSHIBA(8),
    ALIENWARE(9);

    private final Integer code;

    public static Brand getByCode(int code) {
        return stream(values()).filter(marque -> marque.getCode() == code).findFirst().orElseThrow(IllegalArgumentException::new);
    }

}
