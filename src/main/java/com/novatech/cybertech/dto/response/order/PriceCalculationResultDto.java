package com.novatech.cybertech.dto.response.order;

import com.novatech.cybertech.entities.enums.DiscountType;
import com.novatech.cybertech.entities.valueObjects.CurrencyCode;
import com.novatech.cybertech.entities.valueObjects.Money;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceCalculationResultDto {

    private BigDecimal baseAmount;

    private BigDecimal discountAmount;

    /**
     * Shipping fee computed by the {@link com.novatech.cybertech.factory.ShippingProviderStrategyFactory}
     * for the requested {@code shippingProvider} / {@code shippingType} pair. Already folded into
     * {@link #finalAmount} — exposed separately so callers / receipts can show the breakdown.
     */
    private BigDecimal shippingCost;

    /**
     * Post-discount, post-shipping total: {@code max(baseAmount - discountAmount, 0) + shippingCost}.
     * The shipping fee is added AFTER clamping the discounted subtotal at zero so a 100% discount
     * still leaves the customer paying the shipping fee.
     */
    private BigDecimal finalAmount;

    private CurrencyCode currencyCode;

    private DiscountType discountType;

    public Money asFinalMoney() {
        return new Money(finalAmount, currencyCode);
    }
}
