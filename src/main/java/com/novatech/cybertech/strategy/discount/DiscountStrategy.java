package com.novatech.cybertech.strategy.discount;

import com.novatech.cybertech.dto.data.DiscountContext;
import com.novatech.cybertech.dto.request.order.OrderItemPriceDto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pluggable discount-calculation algorithm.
 *
 * <p>Implementations are keyed by {@link com.novatech.cybertech.entities.enums.DiscountCalculationType}
 * via the {@code @DiscountTypeHandler} annotation, so several commercial discount types
 * (BLACK_FRIDAY / WINTER_SALES / SPRING_SALES) can reuse the same algorithm
 * (e.g. {@code PercentageDiscountStrategy}).
 *
 * <p>The full {@code DiscountContext} (percentage, fixedAmount, min/max bounds, validity
 * window) is supplied at call time so the strategy is stateless and can react to runtime
 * configuration changes pushed via the admin endpoint.
 *
 * @param baseAmount the pre-discount cart total (always non-null, scale 2)
 * @param items      raw line items — required by per-item algorithms (BOGO)
 * @param context    runtime campaign config (percentage, fixedAmount, bounds…)
 * @return the discount amount to subtract from {@code baseAmount}, scale 2 HALF_UP
 */
public interface DiscountStrategy {
    BigDecimal calculateDiscount(BigDecimal baseAmount, List<OrderItemPriceDto> items, DiscountContext context);
}
