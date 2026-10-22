package com.novatech.cybertech.entities.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SexEnumTest {

    @Test
    void hasTwoValues() {
        assertThat(Sex.values()).containsExactly(Sex.M, Sex.F);
    }

    @Test
    void canonicalCodeMapping() {
        assertThat(Sex.M.getCode()).isEqualTo(1);
        assertThat(Sex.F.getCode()).isEqualTo(2);
    }
}
