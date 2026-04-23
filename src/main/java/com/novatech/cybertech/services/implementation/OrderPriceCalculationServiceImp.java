package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.config.ActiveDiscountsProperties;
import com.novatech.cybertech.dto.request.order.OrderItemPriceDto;
import com.novatech.cybertech.dto.request.order.PriceCalculationRequestDto;
import com.novatech.cybertech.dto.response.order.PriceCalculationResultDto;
import com.novatech.cybertech.entities.enums.DiscountType;
import com.novatech.cybertech.exceptions.DiscountTypeNotActiveException;
import com.novatech.cybertech.factory.DiscountStrategyFactory;
import com.novatech.cybertech.services.core.OrderPriceCalculationService;
import com.novatech.cybertech.strategy.discount.DiscountStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPriceCalculationServiceImp implements OrderPriceCalculationService {

    private final DiscountStrategyFactory discountStrategyFactory;
    private final ActiveDiscountsProperties activeDiscountsProperties;

    @Override
    public PriceCalculationResultDto calculate(final PriceCalculationRequestDto request) {
        final DiscountType discountType = request.getDiscountType();

        if (!activeDiscountsProperties.isActive(discountType)) {
            throw new DiscountTypeNotActiveException(
                    "DiscountType " + discountType + " is not currently active. Active types: "
                            + activeDiscountsProperties.getEnabled());
        }

        final BigDecimal baseAmount = request.getItems().stream()
                .map(this::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        final BigDecimal discountAmount = discountType == DiscountType.NO_DISCOUNT
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : resolveStrategy(discountType).calculateDiscount(baseAmount);

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

    private BigDecimal lineTotal(final OrderItemPriceDto item) {
        return item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
    }

    private DiscountStrategy resolveStrategy(final DiscountType type) {
        final DiscountStrategy strategy = discountStrategyFactory.getStrategy(type);
        if (strategy == null) {
            throw new DiscountTypeNotActiveException(
                    "No DiscountStrategy is wired for active DiscountType " + type
                            + " — check strategy beans and DiscountTypeHandler annotations.");
        }
        return strategy;
    }
}
