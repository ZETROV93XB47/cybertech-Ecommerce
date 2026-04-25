package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.DiscountContext;
import com.novatech.cybertech.dto.request.order.OrderItemPriceDto;
import com.novatech.cybertech.dto.request.order.PriceCalculationRequestDto;
import com.novatech.cybertech.dto.response.order.PriceCalculationResultDto;
import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import com.novatech.cybertech.entities.enums.DiscountType;
import com.novatech.cybertech.exceptions.DiscountTypeNotActiveException;
import com.novatech.cybertech.factory.DiscountStrategyFactory;
import com.novatech.cybertech.services.core.DiscountCampaignService;
import com.novatech.cybertech.services.core.OrderPriceCalculationService;
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

    private final DiscountStrategyFactory discountStrategyFactory;
    private final DiscountCampaignService discountCampaignService;

    @Override
    public PriceCalculationResultDto calculate(final PriceCalculationRequestDto request) {
        final DiscountType discountType = request.getDiscountType();

        final BigDecimal baseAmount = request.getItems().stream()
                .map(this::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // Throws DiscountTypeNotActiveException when the campaign is missing,
        // disabled, or outside its [startsAt, endsAt] window.
        final DiscountContext context = discountCampaignService.getActiveDiscountContext(discountType);

        final BigDecimal discountAmount = computeDiscount(baseAmount, request.getItems(), context);

        final BigDecimal finalAmount = baseAmount.subtract(discountAmount).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        log.info("Price calculation for {} items — base={} discount={} final={} ({} / {})",
                request.getItems().size(), baseAmount, discountAmount, finalAmount,
                discountType, request.getCurrencyCode());

        return PriceCalculationResultDto.builder()
                .baseAmount(baseAmount)
                .discountAmount(discountAmount)
                .finalAmount(finalAmount)
                .currencyCode(request.getCurrencyCode())
                .discountType(discountType)
                .build();
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
