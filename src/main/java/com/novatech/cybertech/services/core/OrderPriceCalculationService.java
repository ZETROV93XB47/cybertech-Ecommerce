package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.data.DiscountContext;
import com.novatech.cybertech.dto.request.order.PriceCalculationRequestDto;
import com.novatech.cybertech.dto.response.order.PriceCalculationResultDto;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.entities.valueObjects.DeliveryWindow;

import java.math.BigDecimal;

public interface OrderPriceCalculationService {

    /**
     * Price a new order with the CURRENT terms of the campaign {@code request.discountKey}.
     *
     * @throws com.novatech.cybertech.exceptions.DiscountTypeNotActiveException when the campaign
     *         is missing, disabled or outside its validity window.
     */
    PriceCalculationResultDto calculate(PriceCalculationRequestDto request);

    /**
     * Re-price an existing order with explicit discount terms — the ones it was placed under — so
     * that the campaign's current state (disabled, edited, expired) never changes what the order
     * was granted. The terms are applied exactly like at creation: percentage and its cap, BOGO
     * per line, fixed amount bounded by the subtotal, and no discount at all when the new
     * subtotal is below {@code minOrderAmount}.
     *
     * @param request       new lines and shipping options; {@code discountKey} is the order's.
     * @param discountTerms the order's frozen terms; {@code null} for an order placed before the
     *                      terms were snapshotted, which then falls back to the campaign's current
     *                      terms if it is still active, else to no discount (logged in warn).
     */
    PriceCalculationResultDto calculate(PriceCalculationRequestDto request, DiscountContext discountTerms);

    /**
     * Shipping fee of a (provider, type) pair, rounded to 2 decimals — exactly the
     * {@code shippingCost} both {@code calculate} methods fold into an order's total.
     *
     * @throws com.novatech.cybertech.exceptions.NoStrategyFoundForProcessingTheRequest when no
     *         shipping service is wired for {@code shippingProvider}.
     */
    BigDecimal calculateShippingCost(ShippingProvider shippingProvider, ShippingType shippingType);

    /**
     * Indicative delivery time of a (provider, type) pair — exactly the window both
     * {@code calculate} methods return, from which an order's estimated delivery dates are set.
     *
     * @throws com.novatech.cybertech.exceptions.NoStrategyFoundForProcessingTheRequest when no
     *         shipping service is wired for {@code shippingProvider}.
     */
    DeliveryWindow getDeliveryWindow(ShippingProvider shippingProvider, ShippingType shippingType);
}
