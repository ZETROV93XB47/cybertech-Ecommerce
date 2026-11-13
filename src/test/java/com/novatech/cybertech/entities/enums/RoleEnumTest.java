package com.novatech.cybertech.entities.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RoleEnumTest {

    @Test
    void hasExactlyTwoRoles() {
        assertThat(Role.values()).containsExactly(Role.USER, Role.ADMIN);
    }

    @ParameterizedTest
    @CsvSource({
            "USER,1",
            "ADMIN,2"
    })
    void everyRoleExposesItsCode(final String name, final Integer expectedCode) {
        assertThat(Role.valueOf(name).getCode()).isEqualTo(expectedCode);
    }

    @Test
    void codesAreDistinct() {
        assertThat(Role.values())
                .extracting(Role::getCode)
                .doesNotHaveDuplicates();
    }
}
