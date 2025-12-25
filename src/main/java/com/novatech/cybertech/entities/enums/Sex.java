package com.novatech.cybertech.entities.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static java.util.Arrays.stream;

@Getter
@RequiredArgsConstructor
public enum Sex {
    M(1),
    F(2);
    private final int code;

    public static Sex getByCode(int code) {
        return stream(values()).filter(sex -> sex.getCode() == code).findFirst().orElseThrow(IllegalArgumentException::new);
    }
}
