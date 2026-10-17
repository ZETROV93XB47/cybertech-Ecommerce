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
 * Buy-one-get-one-free: per item line, every second unit is free, so the
 * discount per line is {@code (quantity / 2) * unitPrice}.
 *
 * <p>{@code baseAmount} is ignored in favour of the raw line items: the algorithm is
 * intrinsically per-line.
 */
@Component
@DiscountTypeHandler({DiscountCalculationType.BUY_ONE_GET_ONE_FREE})
public class BuyOneGetOneFreeDiscountStrategy implements DiscountStrategy {

    @Override
    public BigDecimal calculateDiscount(final BigDecimal baseAmount,
                                        final List<OrderItemPriceDto> items,
                                        final DiscountContext context) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal discount = items.stream()
                .map(this::lineDiscount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (context.maxDiscountAmount() != null) {
            discount = discount.min(context.maxDiscountAmount());
        }

        return discount.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal lineDiscount(final OrderItemPriceDto item) {
        final int freeUnits = item.getQuantity() / 2;
        return item.getUnitPrice().multiply(BigDecimal.valueOf(freeUnits));
    }
}
