package com.novatech.cybertech.entities.enums;

import java.util.stream.Stream;

public interface EnumFunctions<C extends Number> {

    C getCode();

    static <T extends EnumFunctions<C>, C extends Number> T getByCode(final C code, final Class<T> enumClass) {
        if (code == null) throw new IllegalArgumentException("code cannot be null for : " + enumClass.getName());

        return Stream.of(enumClass.getEnumConstants())
                .filter(e -> e.getCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No Enum Constant with code : " + code + " for Enum  class : " + enumClass.getName()));
    }
}
