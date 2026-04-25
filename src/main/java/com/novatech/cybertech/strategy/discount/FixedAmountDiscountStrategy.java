package com.novatech.cybertech.strategy.discount;

import com.novatech.cybertech.annotation.DiscountTypeHandler;
import com.novatech.cybertech.dto.data.DiscountContext;
import com.novatech.cybertech.dto.request.order.OrderItemPriceDto;
import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Fixed-amount discount: subtracts {@code context.fixedAmount()} from the cart total,
 * never going below zero (handled by the orchestrating service via {@code max(ZERO)}).
 */
@Component
@DiscountTypeHandler({DiscountCalculationType.FIXED_AMOUNT})
public class FixedAmountDiscountStrategy implements DiscountStrategy {

    @Override
    public BigDecimal calculateDiscount(final BigDecimal baseAmount,
                                        final List<OrderItemPriceDto> items,
                                        final DiscountContext context) {
        final BigDecimal fixedAmount = context.fixedAmount();
        if (fixedAmount == null) {
            throw new IllegalStateException(
                    "Fixed-amount discount requires a non-null fixedAmount in context for " + context.discountType());
        }

        BigDecimal discount = fixedAmount.min(baseAmount);

        if (context.maxDiscountAmount() != null) {
            discount = discount.min(context.maxDiscountAmount());
        }

        return discount.setScale(2, RoundingMode.HALF_UP);
    }
}
