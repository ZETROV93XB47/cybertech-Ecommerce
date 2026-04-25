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
 * Percentage discount: {@code discount = baseAmount * percentage / 100}.
 *
 * <p>Used by every commercial campaign whose {@code calculationType} is
 * {@link DiscountCalculationType#PERCENTAGE} (BLACK_FRIDAY, WINTER_SALES,
 * SPRING_SALES, …). The percentage value comes from the runtime
 * {@link DiscountContext}, not a hard-coded constant.
 *
 * <p>Caps the discount at {@code context.maxDiscountAmount()} when set.
 */
@Component
@DiscountTypeHandler({DiscountCalculationType.PERCENTAGE})
public class PercentageDiscountStrategy implements DiscountStrategy {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Override
    public BigDecimal calculateDiscount(final BigDecimal baseAmount,
                                        final List<OrderItemPriceDto> items,
                                        final DiscountContext context) {
        final BigDecimal percentage = context.percentage();
        if (percentage == null) {
            throw new IllegalStateException(
                    "Percentage discount requires a non-null percentage in context for " + context.discountType());
        }

        BigDecimal discount = baseAmount
                .multiply(percentage)
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);

        if (context.maxDiscountAmount() != null) {
            discount = discount.min(context.maxDiscountAmount());
        }

        return discount.setScale(2, RoundingMode.HALF_UP);
    }
}
