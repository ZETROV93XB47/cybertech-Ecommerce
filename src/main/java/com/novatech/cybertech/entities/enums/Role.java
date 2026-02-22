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

}
