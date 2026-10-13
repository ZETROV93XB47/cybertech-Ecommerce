package com.novatech.cybertech.utils;

import com.novatech.cybertech.dto.response.order.PriceCalculationResultDto;
import com.novatech.cybertech.entities.OrderEntity;

/**
 * Writes a {@link PriceCalculationResultDto} onto an {@link OrderEntity}, shared by order creation
 * and {@code updateOrder} so the persisted breakdown (subtotal, discount, shipping, total) can
 * never drift from the stored total.
 */
public final class OrderPricingUtils {

    private OrderPricingUtils() {
    }

    /**
     * Copy the price breakdown onto the order. Leaves {@code discountKey} and the discount terms
     * snapshot untouched: they are set once, when the order is placed.
     *
     * @param order  the order to update.
     * @param result the price computed for the order's current lines and shipping options.
     */
    public static void applyPriceBreakdown(final OrderEntity order, final PriceCalculationResultDto result) {
        order.setTotalAmount(result.asFinalMoney());
        order.setSubtotalAmount(result.getBaseAmount());
        order.setDiscountAmount(result.getDiscountAmount());
        order.setShippingCost(result.getShippingCost());
    }
}
