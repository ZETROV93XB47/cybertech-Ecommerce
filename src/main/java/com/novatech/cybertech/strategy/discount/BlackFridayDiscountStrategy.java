package com.novatech.cybertech.strategy.discount;

import com.novatech.cybertech.annotation.DiscountTypeHandler;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.enums.DiscountType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@DiscountTypeHandler(DiscountType.BLACK_FRIDAY)
public class BlackFridayDiscountStrategy implements DiscountStrategy {
    @Override
    public BigDecimal calculateDiscount(OrderEntity orderEntity) {
        return BigDecimal.valueOf(orderEntity.getDiscountType().getDiscountPercentage() * orderEntity.getTotalAmount().doubleValue());
    }
}
