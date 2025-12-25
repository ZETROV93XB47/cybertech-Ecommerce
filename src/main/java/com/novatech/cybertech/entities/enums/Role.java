package com.novatech.cybertech.entities.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

import static java.util.Arrays.stream;

@Getter
@RequiredArgsConstructor
public enum Role {

    USER(1),
    ADMIN(2);
    private final Integer code;

    public static Role getByCode(Integer code) {
        return stream(values()).filter(role -> Objects.equals(role.getCode(), code)).findFirst().orElseThrow(IllegalArgumentException::new);
    }

}
