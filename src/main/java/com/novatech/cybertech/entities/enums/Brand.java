package com.novatech.cybertech.entities.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static java.util.Arrays.stream;

@Getter
@RequiredArgsConstructor
public enum Brand {

    ASUS(1),
    HP(2),
    LENOVO(3),
    DELL(4),
    AORUS(5),
    MSI(6),
    ACER(7),
    TOSHIBA(8),
    ALIENWARE(9),
    MICROSOFT(10),
    ALTYK(11),
    GIGABYTE(12),
    SAMSUNG(13),
    // Monitor brands (LDLC catalog)
    LG(14),
    AOC(15),
    IIYAMA(16),
    BENQ(17),
    PHILIPS(18),
    VIEWSONIC(19),
    EIZO(20),
    CORSAIR(21),
    LDLC(22),
    XIAOMI(23),
    // Smartwatch brands (LDLC catalog)
    APPLE(24),
    GARMIN(25),
    HUAWEI(26),
    AMAZFIT(27),
    WITHINGS(28),
    GOOGLE(29),
    POLAR(30),
    SUUNTO(31),
    COROS(32),
    FITBIT(33);

    private final Integer code;

}
