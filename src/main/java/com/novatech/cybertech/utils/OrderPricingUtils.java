package com.novatech.cybertech.utils;

import com.novatech.cybertech.dto.request.order.OrderItemPriceDto;
import com.novatech.cybertech.dto.request.order.PriceCalculationRequestDto;
import com.novatech.cybertech.dto.response.order.PriceCalculationResultDto;
import com.novatech.cybertech.entities.CartEntity;
import com.novatech.cybertech.entities.CartItemEntity;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.entities.valueObjects.CurrencyCode;
import com.novatech.cybertech.exceptions.CartIsEmptyException;

import java.util.List;

/**
 * Order pricing helpers shared by order creation, {@code updateOrder} and the order quote, so a
 * quote is priced exactly like the order placed right after it, and the persisted breakdown
 * (subtotal, discount, shipping, total) can never drift from the stored total.
 */
public final class OrderPricingUtils {

    /** Every order is priced in EUR today. */
    public static final CurrencyCode ORDER_CURRENCY = CurrencyCode.EUR;

    private OrderPricingUtils() {
    }

    /**
     * The lines of the cart an order (or a quote) is built from.
     *
     * @param cart         the caller's cart; may be {@code null} (no cart yet).
     * @param errorMessage message of the exception when there is nothing to order.
     * @return the cart items, never empty.
     * @throws CartIsEmptyException (409 CART_IS_EMPTY) when the cart is missing or empty.
     */
    public static List<CartItemEntity> requireCartItems(final CartEntity cart, final String errorMessage) {
        if (cart == null || cart.getCartItems() == null || cart.getCartItems().isEmpty()) {
            throw new CartIsEmptyException(errorMessage);
        }
        return cart.getCartItems();
    }

    /**
     * Price request for the given cart lines at the products' CURRENT prices — the cart itself
     * carries no frozen price (see {@code CartItemEntity}).
     */
    public static PriceCalculationRequestDto cartPriceRequest(final List<CartItemEntity> cartItems, final String discountKey,
                                                              final ShippingProvider shippingProvider, final ShippingType shippingType) {
        final List<OrderItemPriceDto> items = cartItems.stream()
                .map(item -> OrderItemPriceDto.builder()
                        .productUuid(item.getProductEntity().getUuid())
                        .unitPrice(item.getProductEntity().getPrice())
                        .quantity(item.getQuantity())
                        .build())
                .toList();
        return PriceCalculationRequestDto.builder()
                .items(items)
                .discountKey(discountKey)
                .currencyCode(ORDER_CURRENCY)
                .shippingProvider(shippingProvider)
                .shippingType(shippingType)
                .build();
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

    /**
     * Sets the order's indicative delivery dates from the delivery window of the shipping option it
     * was just priced with, counted from its {@code orderDate}. Left untouched when either is missing.
     */
    public static void applyDeliveryEstimate(final OrderEntity order, final PriceCalculationResultDto result) {
        if (result.getDeliveryWindow() == null || order.getOrderDate() == null) {
            return;
        }
        final DeliveryEstimateUtils.DeliveryEstimate estimate =
                DeliveryEstimateUtils.estimate(result.getDeliveryWindow(), order.getOrderDate());
        order.setEstimatedDeliveryFrom(estimate.from());
        order.setEstimatedDeliveryTo(estimate.to());
    }
}
