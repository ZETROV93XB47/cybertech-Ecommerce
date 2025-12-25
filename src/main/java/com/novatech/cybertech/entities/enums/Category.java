package com.novatech.cybertech.entities.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static java.util.Arrays.stream;

@Getter
@RequiredArgsConstructor
public enum Category {

    COMPUTER(1),
    MONITOR(2);
    //MACBOOK(3),
    //KEYBOARD(4),
    //SMARTPHONE(5);

    private final Integer code;

    public static Category getByCode(int code) {
        return stream(values())
                .filter(category -> category.getCode().equals(code))
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
    }
}
