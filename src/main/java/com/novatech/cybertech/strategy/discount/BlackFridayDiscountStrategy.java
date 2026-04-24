package com.novatech.cybertech.strategy.discount;

import com.novatech.cybertech.annotation.DiscountTypeHandler;
import com.novatech.cybertech.entities.enums.DiscountType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@DiscountTypeHandler(DiscountType.BLACK_FRIDAY)
public class BlackFridayDiscountStrategy implements DiscountStrategy {

    // Previously derived from DiscountType.BLACK_FRIDAY.getDiscountPercentage() (0.4f).
    // Field removed from enum; hardcoded here pending full DiscountCampaign refactor.
    private static final BigDecimal PERCENTAGE = BigDecimal.valueOf(0.4);

    @Override
    public BigDecimal calculateDiscount(final BigDecimal baseAmount) {
        return baseAmount.multiply(PERCENTAGE).setScale(2, RoundingMode.HALF_UP);
    }
}
