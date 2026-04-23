package com.novatech.cybertech.strategy.discount;

import com.novatech.cybertech.annotation.DiscountTypeHandler;
import com.novatech.cybertech.entities.enums.DiscountType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@DiscountTypeHandler(DiscountType.BLACK_FRIDAY)
public class BlackFridayDiscountStrategy implements DiscountStrategy {

    private static final BigDecimal PERCENTAGE = BigDecimal.valueOf(DiscountType.BLACK_FRIDAY.getDiscountPercentage());

    @Override
    public BigDecimal calculateDiscount(final BigDecimal baseAmount) {
        return baseAmount.multiply(PERCENTAGE).setScale(2, RoundingMode.HALF_UP);
    }
}
