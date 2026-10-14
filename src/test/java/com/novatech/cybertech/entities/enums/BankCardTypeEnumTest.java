package com.novatech.cybertech.entities.enums;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;

import static org.assertj.core.api.Assertions.assertThat;

class BankCardTypeEnumTest {

    @ParameterizedTest
    @CsvSource({"visa,VISA", "mastercard,MASTERCARD", "amex,AMERICAN_EXPRESS", "discover,DISCOVER",
            "unionpay,OTHER", "jcb,OTHER", "cartes_bancaires,OTHER"})
    void mapsStripeBrands(final String stripeBrand, final BankCardType expected) {
        assertThat(BankCardType.fromStripeBrand(stripeBrand)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    void nullBrandIsOther(final String stripeBrand) {
        assertThat(BankCardType.fromStripeBrand(stripeBrand)).isEqualTo(BankCardType.OTHER);
    }
}
