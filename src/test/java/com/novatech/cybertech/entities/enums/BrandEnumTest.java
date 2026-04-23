package com.novatech.cybertech.entities.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class BrandEnumTest {

    @Test
    void hasExpectedConstantCount() {
        assertThat(Brand.values()).hasSize(13);
    }

    @ParameterizedTest
    @EnumSource(Brand.class)
    void everyBrandHasNonNullCode(final Brand brand) {
        assertThat(brand.getCode()).isNotNull().isPositive();
    }

    @Test
    void codesAreUniqueAcrossBrands() {
        assertThat(Arrays.stream(Brand.values()).map(Brand::getCode).toList())
                .doesNotHaveDuplicates();
    }

    @Test
    void contractAnchorMappings() {
        assertThat(Brand.ASUS.getCode()).isEqualTo(1);
        assertThat(Brand.HP.getCode()).isEqualTo(2);
        assertThat(Brand.SAMSUNG.getCode()).isEqualTo(13);
    }
}
