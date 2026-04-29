package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.DiscountContext;
import com.novatech.cybertech.dto.request.order.OrderItemPriceDto;
import com.novatech.cybertech.dto.request.order.PriceCalculationRequestDto;
import com.novatech.cybertech.dto.response.order.PriceCalculationResultDto;
import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import com.novatech.cybertech.entities.enums.DiscountType;
import com.novatech.cybertech.exceptions.DiscountTypeNotActiveException;
import com.novatech.cybertech.exceptions.NoStrategyFoundForProcessingTheRequest;
import com.novatech.cybertech.factory.DiscountStrategyFactory;
import com.novatech.cybertech.factory.ShippingProviderStrategyFactory;
import com.novatech.cybertech.services.core.DiscountCampaignService;
import com.novatech.cybertech.services.core.OrderPriceCalculationService;
import com.novatech.cybertech.services.core.ShippingProviderService;
import com.novatech.cybertech.strategy.discount.DiscountStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Orchestrates the cart-total → final-total calculation:
 * <ol>
 *   <li>Resolve the active {@link DiscountContext} from {@link DiscountCampaignService}
 *       (DB-driven, validates {@code enabled}, {@code startsAt}, {@code endsAt}).</li>
 *   <li>Pick the matching algorithm via {@link DiscountStrategyFactory} keyed on
 *       {@link DiscountCalculationType}.</li>
 *   <li>Honour {@code minOrderAmount} (no discount applied if base &lt; min).</li>
 *   <li>Subtract the discount, clamping the result at {@code 0}.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPriceCalculationServiceImp implements OrderPriceCalculationService {

    private static final String NO_SHIPPING_STRATEGY_MESSAGE_PREFIX = "No ShippingProviderService wired for provider ";

    private final DiscountStrategyFactory discountStrategyFactory;
    private final DiscountCampaignService discountCampaignService;
    private final ShippingProviderStrategyFactory shippingProviderStrategyFactory;

    @Override
    public PriceCalculationResultDto calculate(final PriceCalculationRequestDto request) {
        final DiscountType discountType = request.getDiscountType();

        final BigDecimal baseAmount = request.getItems().stream()
                .map(this::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // FIX(SHIPPING-INT): include shipping cost computed via ShippingProviderStrategyFactory in final order price (previously omitted)
        final BigDecimal shippingCost = computeShippingCost(request);

        // Short-circuit for NO_DISCOUNT: avoid touching the discount_campaign table.
        // A missing NO_DISCOUNT row used to break every placeOrder call with
        // DiscountTypeNotActiveException — there is nothing to look up here.
        if (discountType == DiscountType.NO_DISCOUNT) {
            final BigDecimal finalAmount = baseAmount.add(shippingCost)
                    .setScale(2, RoundingMode.HALF_UP);
            return PriceCalculationResultDto.builder()
                    .baseAmount(baseAmount)
                    .discountAmount(zero())
                    .shippingCost(shippingCost)
                    .finalAmount(finalAmount)
                    .currencyCode(request.getCurrencyCode())
                    .discountType(discountType)
                    .build();
        }

        // Throws DiscountTypeNotActiveException when the campaign is missing,
        // disabled, or outside its [startsAt, endsAt] window.
        final DiscountContext context = discountCampaignService.getActiveDiscountContext(discountType);

        final BigDecimal discountAmount = computeDiscount(baseAmount, request.getItems(), context);

        // Shipping is added AFTER clamping the post-discount subtotal at zero, so a 100% discount
        // still leaves the customer paying the shipping fee.
        final BigDecimal discountedSubtotal = baseAmount.subtract(discountAmount).max(BigDecimal.ZERO);
        final BigDecimal finalAmount = discountedSubtotal.add(shippingCost)
                .setScale(2, RoundingMode.HALF_UP);

        log.info("Price calculation for {} items — base={} discount={} shipping={} final={} ({} / {} / {} / {})",
                request.getItems().size(), baseAmount, discountAmount, shippingCost, finalAmount,
                discountType, request.getCurrencyCode(), request.getShippingProvider(), request.getShippingType());

        return PriceCalculationResultDto.builder()
                .baseAmount(baseAmount)
                .discountAmount(discountAmount)
                .shippingCost(shippingCost)
                .finalAmount(finalAmount)
                .currencyCode(request.getCurrencyCode())
                .discountType(discountType)
                .build();
    }

    private BigDecimal computeShippingCost(final PriceCalculationRequestDto request) {
        final ShippingProviderService shippingStrategy = shippingProviderStrategyFactory.getStrategy(request.getShippingProvider());
        if (shippingStrategy == null) {
            throw new NoStrategyFoundForProcessingTheRequest(
                    NO_SHIPPING_STRATEGY_MESSAGE_PREFIX + request.getShippingProvider());
        }
        return shippingStrategy.calculateShippingCost(request.getShippingType())
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal computeDiscount(final BigDecimal baseAmount,
                                       final java.util.List<OrderItemPriceDto> items,
                                       final DiscountContext context) {
        if (context.calculationType() == DiscountCalculationType.NONE) {
            return zero();
        }

        if (context.minOrderAmount() != null
                && baseAmount.compareTo(context.minOrderAmount()) < 0) {
            log.debug("Base amount {} below minOrderAmount {} for {} — no discount applied",
                    baseAmount, context.minOrderAmount(), context.discountType());
            return zero();
        }

        final DiscountStrategy strategy = discountStrategyFactory.getStrategy(context.calculationType());
        if (strategy == null) {
            throw new DiscountTypeNotActiveException(
                    "No DiscountStrategy wired for calculation type " + context.calculationType()
                            + " (campaign=" + context.discountType() + ")");
        }

        return strategy.calculateDiscount(baseAmount, items, context);
    }

    private BigDecimal lineTotal(final OrderItemPriceDto item) {
        return item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
