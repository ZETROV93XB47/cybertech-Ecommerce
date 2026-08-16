package com.novatech.cybertech.entities.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BUG-DISCOUNT-CAMPAIGN-001: {@code discountPercentage} was removed from {@link DiscountType} as
 * part of the DiscountCampaign refactor — percentages now live in the {@code discount_campaign}
 * table. Canonical values (BLACK_FRIDAY=20, WINTER_SALES=15, SPRING_SALES=10, ...) are seeded by
 * {@code DiscountCampaignInitializer} and covered by {@code DiscountCampaignInitializerTest}
 * (e.g. {@code blackFridaySeededWithCorrectDefaults}); the two disabled placeholder tests that
 * used to pin the old, now-nonexistent field here were removed as redundant.
 */
class DiscountTypeEnumTest {

    @Test
    void hasFiveDiscountTypes() {
        assertThat(DiscountType.values()).hasSize(5);
    }
}
