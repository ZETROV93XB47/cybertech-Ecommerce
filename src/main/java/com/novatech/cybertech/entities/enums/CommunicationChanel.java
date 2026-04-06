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
    SMS(2),
    PUSH_NOTIFICATION(3);

    private final Integer code;
}