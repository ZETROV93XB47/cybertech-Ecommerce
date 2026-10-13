package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.DiscountContext;
import com.novatech.cybertech.dto.request.order.OrderItemPriceDto;
import com.novatech.cybertech.dto.request.order.PriceCalculationRequestDto;
import com.novatech.cybertech.dto.response.order.PriceCalculationResultDto;
import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
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

import static com.novatech.cybertech.constants.CyberTechAppConstants.NO_DISCOUNT_KEY;

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
    private static final DiscountContext NO_DISCOUNT_CONTEXT = DiscountContext.builder()
            .discountKey(NO_DISCOUNT_KEY)
            .calculationType(DiscountCalculationType.NONE)
            .build();

    private final DiscountStrategyFactory discountStrategyFactory;
    private final DiscountCampaignService discountCampaignService;
    private final ShippingProviderStrategyFactory shippingProviderStrategyFactory;

    @Override
    public PriceCalculationResultDto calculate(final PriceCalculationRequestDto request) {
        // Short-circuit for NO_DISCOUNT_KEY: avoid touching the discount_campaign table.
        // A missing NO_DISCOUNT row used to break every placeOrder call with
        // DiscountTypeNotActiveException — there is nothing to look up here.
        // Otherwise throws DiscountTypeNotActiveException when the campaign is missing,
        // disabled, or outside its [startsAt, endsAt] window.
        final DiscountContext context = NO_DISCOUNT_KEY.equals(request.getDiscountKey())
                ? NO_DISCOUNT_CONTEXT
                : discountCampaignService.getActiveDiscountContext(request.getDiscountKey());
        return price(request, context);
    }

    @Override
    public PriceCalculationResultDto calculate(final PriceCalculationRequestDto request, final DiscountContext discountTerms) {
        return price(request, discountTerms != null ? discountTerms : resolveTermsWithoutSnapshot(request.getDiscountKey()));
    }

    /**
     * Terms for an order placed before its discount terms were snapshotted: the campaign's current
     * terms if it is still active, else no discount. Logged in warn either way — the customer may
     * not get exactly the terms they ordered under.
     */
    private DiscountContext resolveTermsWithoutSnapshot(final String discountKey) {
        if (NO_DISCOUNT_KEY.equals(discountKey)) {
            return NO_DISCOUNT_CONTEXT;
        }
        try {
            final DiscountContext current = discountCampaignService.getActiveDiscountContext(discountKey);
            log.warn("No discount terms snapshot for campaign {} — re-pricing with the campaign's current terms", discountKey);
            return current;
        } catch (final DiscountTypeNotActiveException e) {
            log.warn("No discount terms snapshot and campaign {} is no longer active — re-pricing without discount", discountKey);
            return NO_DISCOUNT_CONTEXT;
        }
    }

    private PriceCalculationResultDto price(final PriceCalculationRequestDto request, final DiscountContext context) {
        final BigDecimal baseAmount = request.getItems().stream()
                .map(this::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // Include shipping cost computed via ShippingProviderStrategyFactory in final order price (previously omitted)
        final BigDecimal shippingCost = calculateShippingCost(request.getShippingProvider(), request.getShippingType());

        final BigDecimal discountAmount = computeDiscount(baseAmount, request.getItems(), context);

        // Shipping is added AFTER clamping the post-discount subtotal at zero, so a 100% discount
        // still leaves the customer paying the shipping fee.
        final BigDecimal discountedSubtotal = baseAmount.subtract(discountAmount).max(BigDecimal.ZERO);
        final BigDecimal finalAmount = discountedSubtotal.add(shippingCost)
                .setScale(2, RoundingMode.HALF_UP);

        log.info("Price calculation for {} items — base={} discount={} shipping={} final={} ({} / {} / {} / {})",
                request.getItems().size(), baseAmount, discountAmount, shippingCost, finalAmount,
                request.getDiscountKey(), request.getCurrencyCode(), request.getShippingProvider(), request.getShippingType());

        return PriceCalculationResultDto.builder()
                .baseAmount(baseAmount)
                .discountAmount(discountAmount)
                .shippingCost(shippingCost)
                .finalAmount(finalAmount)
                .currencyCode(request.getCurrencyCode())
                .discountKey(request.getDiscountKey())
                .appliedDiscount(context)
                .build();
    }

    @Override
    public BigDecimal calculateShippingCost(final ShippingProvider shippingProvider, final ShippingType shippingType) {
        final ShippingProviderService shippingStrategy = shippingProviderStrategyFactory.getStrategy(shippingProvider);
        if (shippingStrategy == null) {
            throw new NoStrategyFoundForProcessingTheRequest(NO_SHIPPING_STRATEGY_MESSAGE_PREFIX + shippingProvider);
        }

        return shippingStrategy.calculateShippingCost(shippingType).setScale(2, RoundingMode.HALF_UP);
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
                    baseAmount, context.minOrderAmount(), context.discountKey());
            return zero();
        }

        final DiscountStrategy strategy = discountStrategyFactory.getStrategy(context.calculationType());
        if (strategy == null) {
            throw new DiscountTypeNotActiveException(
                    "No DiscountStrategy wired for calculation type " + context.calculationType()
                            + " (campaign=" + context.discountKey() + ")");
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
