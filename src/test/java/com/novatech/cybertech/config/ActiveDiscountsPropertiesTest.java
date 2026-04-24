package com.novatech.cybertech.config;

import com.novatech.cybertech.entities.enums.DiscountType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveDiscountsPropertiesTest {

    private ActiveDiscountsProperties props() {
        ActiveDiscountsProperties p = new ActiveDiscountsProperties();
        // defaults: noDiscount=true, everything else false
        return p;
    }

    @Test
    @DisplayName("default: only NO_DISCOUNT is active")
    void defaultOnlyNoDiscountActive() {
        ActiveDiscountsProperties p = props();
        assertThat(p.isActive(DiscountType.NO_DISCOUNT)).isTrue();
        assertThat(p.isActive(DiscountType.BLACK_FRIDAY)).isFalse();
        assertThat(p.isActive(DiscountType.WINTER_SALES)).isFalse();
        assertThat(p.isActive(DiscountType.SPRING_SALES)).isFalse();
        assertThat(p.isActive(DiscountType.BUY_ONE_GET_ONE_FREE)).isFalse();
    }

    @Test
    @DisplayName("isActive returns false for null")
    void isActiveReturnsFalseForNull() {
        assertThat(props().isActive(null)).isFalse();
    }

    @Test
    @DisplayName("enabling BLACK_FRIDAY makes it active")
    void enablingBlackFriday() {
        ActiveDiscountsProperties p = props();
        p.setBlackFriday(true);
        assertThat(p.isActive(DiscountType.BLACK_FRIDAY)).isTrue();
    }

    @Test
    @DisplayName("getEnabled returns only active discount types")
    void getEnabledReturnsActiveTypes() {
        ActiveDiscountsProperties p = props();
        p.setBlackFriday(true);
        Set<DiscountType> enabled = p.getEnabled();
        assertThat(enabled).containsExactlyInAnyOrder(DiscountType.NO_DISCOUNT, DiscountType.BLACK_FRIDAY);
    }

    @Test
    @DisplayName("disabling noDiscount removes it from getEnabled")
    void disablingNoDiscount() {
        ActiveDiscountsProperties p = props();
        p.setNoDiscount(false);
        assertThat(p.getEnabled()).isEmpty();
    }
}
