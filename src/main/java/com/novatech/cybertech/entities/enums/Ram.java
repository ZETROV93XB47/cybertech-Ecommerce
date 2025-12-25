package com.novatech.cybertech.entities.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static java.util.Arrays.stream;

@Getter
@RequiredArgsConstructor
public enum Ram {

    GO_16(1, 16),
    GO_32(2, 32),
    GO_64(3, 64),
    GO_128(4, 128),
    GO_256(5, 256);

    private final int code;
    private final int size;

    public static Ram getByCode(int code) {

        return stream(values()).filter(marque -> marque.getCode() == code).findFirst().orElseThrow(IllegalArgumentException::new);

    }
}