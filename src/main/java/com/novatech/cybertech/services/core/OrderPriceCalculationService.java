package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.data.DiscountContext;
import com.novatech.cybertech.dto.request.order.PriceCalculationRequestDto;
import com.novatech.cybertech.dto.response.order.PriceCalculationResultDto;

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
}
