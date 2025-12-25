package com.novatech.cybertech.entities.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SSD implements EnumFunctions<Integer> {

    GO_1024(4, 1024),
    GO_2048(5, 2048),
    GO_4096(6, 4096),
    GO_8192(7, 8192);

    private final Integer code;
    private final Integer size;

    public static SSD getByCode(int code) {
        return EnumFunctions.getByCode(code, SSD.class);
    }
}
