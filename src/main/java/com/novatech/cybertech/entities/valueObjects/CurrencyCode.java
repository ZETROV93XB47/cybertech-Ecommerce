package com.novatech.cybertech.entities.valueObjects;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.Arrays;

/**
 * Closed enumeration of ISO-4217 currency codes supported by the platform.
 * <p>
 * Coverage spans the historical "core" currencies (USD/EUR/GBP/JPY/AUD/CAD/CHF/CNY/SEK/NZD)
 * plus the major emerging-market currencies (INR/BRL/MXN/RUB/KRW/ZAR) added in
 * BUG-133.
 * </p>
 */
@Getter
@ToString
@RequiredArgsConstructor
public enum CurrencyCode {
    USD("USD"),
    EUR("EUR"),
    GBP("GBP"),
    JPY("JPY"),
    AUD("AUD"),
    CAD("CAD"),
    CHF("CHF"),
    CNY("CNY"),
    SEK("SEK"),
    NZD("NZD"),
    INR("INR"),
    BRL("BRL"),
    MXN("MXN"),
    RUB("RUB"),
    KRW("KRW"),
    ZAR("ZAR");

    private final String code;

    /**
     * Resolve a {@link CurrencyCode} by its three-letter ISO code, case-insensitively.
     *
     * @param code the ISO-4217 code (e.g. {@code "EUR"} / {@code "eur"}).
     * @return the matching enum constant.
     * @throws IllegalArgumentException if no entry matches (including {@code null}).
     */
    public static CurrencyCode fromCode(final String code) {
        return Arrays.stream(CurrencyCode.values())
                .filter(c -> c.getCode().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid currency code: " + code));
    }

    /**
     * Alias for {@link #fromCode(String)} aligned with the JSR / Spring naming
     * convention for enum string-factories. Provided so consumers (Jackson,
     * Spring converters, manual lookups) can use either name interchangeably.
     *
     * @param value the ISO-4217 code (case-insensitive).
     * @return the matching enum constant.
     * @throws IllegalArgumentException if no entry matches.
     */
    public static CurrencyCode fromString(final String value) {
        return fromCode(value);
    }
}
