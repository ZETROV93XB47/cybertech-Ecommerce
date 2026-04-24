package com.novatech.cybertech.entities.enums;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class DiscountTypeEnumTest {

    @Test
    void hasFiveDiscountTypes() {
        assertThat(DiscountType.values()).hasSize(5);
    }

    @Disabled("BUG-DISCOUNT-CAMPAIGN-001: discountPercentage field removed from DiscountType enum as part of " +
            "DiscountCampaign refactor. Discount percentages are now stored in discount_campaign table. " +
            "This test pins the OLD contract and must be replaced when DiscountCampaignServiceImp is integrated.")
    @ParameterizedTest
    @EnumSource(DiscountType.class)
    void discountPercentageIsAlwaysWithinPlausibleBounds(final DiscountType type) {
        // All current discount values are >= 0 and <= 1.
        // getDiscountPercentage() no longer exists on DiscountType — see DiscountCampaignEntity.percentage
    }

    @Disabled("BUG-DISCOUNT-CAMPAIGN-001: canonicalDiscountMapping tests the removed discountPercentage field. " +
            "New canonical values are in DiscountCampaignInitializer and tested by DiscountCampaignInitializerTest.")
    @Test
    void canonicalDiscountMapping() {
        // Previously pinned: NO_DISCOUNT=1f, BUY_ONE_GET_ONE_FREE=1f, BLACK_FRIDAY=0.4f,
        // WINTER_SALES=0.2f, SPRING_SALES=0.3f.
        // These are now stored as BigDecimal percentage in discount_campaign table:
        // BLACK_FRIDAY=20, WINTER_SALES=15, SPRING_SALES=10. See DiscountCampaignInitializer.
    }
}
