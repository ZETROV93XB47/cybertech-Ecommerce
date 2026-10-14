package com.novatech.cybertech.dto.response.order;

import com.novatech.cybertech.dto.data.DiscountContext;
import com.novatech.cybertech.entities.valueObjects.CurrencyCode;
import com.novatech.cybertech.entities.valueObjects.DeliveryWindow;
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

    private String discountKey;

    /**
     * Terms of the campaign the price was computed with ({@code calculationType = NONE} for
     * {@code NO_DISCOUNT}). Snapshotted on the order at creation so later re-pricings keep them.
     * Present even when {@code minOrderAmount} was not reached (then {@link #discountAmount} is 0).
     */
    private DiscountContext appliedDiscount;

    /**
     * Indicative delivery time of the priced {@code shippingProvider} / {@code shippingType} pair;
     * the order's estimated delivery dates are derived from it (see {@code OrderPricingUtils}).
     */
    private DeliveryWindow deliveryWindow;

    public Money asFinalMoney() {
        return new Money(finalAmount, currencyCode);
    }
}
