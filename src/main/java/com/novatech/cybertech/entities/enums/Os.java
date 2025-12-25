package com.novatech.cybertech.entities.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static java.util.Arrays.stream;

@Getter
@RequiredArgsConstructor
public enum Os {

    WINDOWS(1),
    LINUX(2),
    MACOS(3);

    private final Integer code;

    public static Os getByCode(int code) {
        return stream(values()).filter(marque -> marque.getCode() == code).findFirst().orElseThrow(IllegalArgumentException::new);
    }
}
