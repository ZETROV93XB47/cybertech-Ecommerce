package com.novatech.cybertech.entities.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static java.util.Arrays.stream;

@Slf4j
@Getter
@RequiredArgsConstructor
public enum CommunicationChanel {
    EMAIL(1),
    SMS(2);

    private final Integer code;

    public static CommunicationChanel getByCode(final Integer code) {
        return stream(values())
                .filter(communicationChanel -> communicationChanel.getCode().equals(code))
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
    }
}