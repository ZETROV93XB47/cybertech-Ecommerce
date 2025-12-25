package com.novatech.cybertech.strategy.discount;

import com.novatech.cybertech.entities.OrderEntity;

import java.math.BigDecimal;

public interface DiscountStrategy {
    BigDecimal calculateDiscount(final OrderEntity orderEntity);
}
