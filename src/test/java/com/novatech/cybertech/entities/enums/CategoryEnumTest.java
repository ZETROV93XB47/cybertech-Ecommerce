package com.novatech.cybertech.entities.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryEnumTest {

    @Test
    void hasFiveCategories() {
        assertThat(Category.values()).hasSize(5);
    }

    @ParameterizedTest
    @EnumSource(Category.class)
    void everyCategoryHasNonNullCode(final Category category) {
        assertThat(category.getCode()).isNotNull().isPositive();
    }

    @Test
    void codesAreContiguousFromOne() {
        assertThat(Arrays.stream(Category.values()).map(Category::getCode).sorted().toList())
                .containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void canonicalCodeMapping() {
        assertThat(Category.COMPUTER.getCode()).isEqualTo(1);
        assertThat(Category.MONITOR.getCode()).isEqualTo(2);
        assertThat(Category.MACBOOK.getCode()).isEqualTo(3);
        assertThat(Category.KEYBOARD.getCode()).isEqualTo(4);
        assertThat(Category.SMARTPHONE.getCode()).isEqualTo(5);
    }
}
