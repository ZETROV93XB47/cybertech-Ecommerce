package com.novatech.cybertech.strategy.discount;

import java.math.BigDecimal;

public interface DiscountStrategy {
    BigDecimal calculateDiscount(BigDecimal baseAmount);
}
