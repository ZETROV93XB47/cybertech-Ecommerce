package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.DiscountContext;
import com.novatech.cybertech.dto.request.order.OrderItemPriceDto;
import com.novatech.cybertech.dto.request.order.PriceCalculationRequestDto;
import com.novatech.cybertech.dto.response.order.PriceCalculationResultDto;
import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import com.novatech.cybertech.entities.enums.DiscountType;
import com.novatech.cybertech.entities.valueObjects.CurrencyCode;
import com.novatech.cybertech.exceptions.DiscountTypeNotActiveException;
import com.novatech.cybertech.factory.DiscountStrategyFactory;
import com.novatech.cybertech.services.core.DiscountCampaignService;
import com.novatech.cybertech.strategy.discount.DiscountStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPriceCalculationServiceImpTest {

    @Mock private DiscountStrategyFactory discountStrategyFactory;
    @Mock private DiscountCampaignService discountCampaignService;
    @Mock private DiscountStrategy strategy;

    @InjectMocks
    private OrderPriceCalculationServiceImp service;

    private OrderItemPriceDto item(final String unitPrice, final int quantity) {
        return OrderItemPriceDto.builder()
                .productUuid(UUID.randomUUID())
                .unitPrice(new BigDecimal(unitPrice))
                .quantity(quantity)
                .build();
    }

    private PriceCalculationRequestDto request(final DiscountType type, final OrderItemPriceDto... items) {
        return PriceCalculationRequestDto.builder()
                .items(List.of(items))
                .discountType(type)
                .currencyCode(CurrencyCode.EUR)
                .build();
    }

    private DiscountContext noneContext(final DiscountType type) {
        return DiscountContext.builder()
                .discountType(type)
                .calculationType(DiscountCalculationType.NONE)
                .build();
    }

    private DiscountContext percentageContext(final DiscountType type, final BigDecimal percentage) {
        return DiscountContext.builder()
                .discountType(type)
                .calculationType(DiscountCalculationType.PERCENTAGE)
                .percentage(percentage)
                .build();
    }

    @Test
    @DisplayName("NONE calc type: discount = 0, factory not consulted")
    void noneSkipsStrategy() {
        when(discountCampaignService.getActiveDiscountContext(DiscountType.NO_DISCOUNT))
                .thenReturn(noneContext(DiscountType.NO_DISCOUNT));

        final PriceCalculationResultDto result = service.calculate(
                request(DiscountType.NO_DISCOUNT, item("10.00", 2), item("5.00", 3)));

        assertThat(result.getBaseAmount()).isEqualByComparingTo("35.00");
        assertThat(result.getDiscountAmount()).isEqualByComparingTo("0.00");
        assertThat(result.getFinalAmount()).isEqualByComparingTo("35.00");
        assertThat(result.getDiscountType()).isEqualTo(DiscountType.NO_DISCOUNT);
        assertThat(result.getCurrencyCode()).isEqualTo(CurrencyCode.EUR);
        verify(discountStrategyFactory, never()).getStrategy(any());
    }

    @Test
    @DisplayName("PERCENTAGE: factory resolved by calc type, items + context forwarded to strategy")
    void percentageDelegatesToStrategy() {
        final DiscountContext ctx = percentageContext(DiscountType.BLACK_FRIDAY, new BigDecimal("40"));
        when(discountCampaignService.getActiveDiscountContext(DiscountType.BLACK_FRIDAY)).thenReturn(ctx);
        when(discountStrategyFactory.getStrategy(DiscountCalculationType.PERCENTAGE)).thenReturn(strategy);
        when(strategy.calculateDiscount(any(BigDecimal.class), anyList(), any(DiscountContext.class)))
                .thenReturn(new BigDecimal("40.00"));

        final PriceCalculationResultDto result = service.calculate(
                request(DiscountType.BLACK_FRIDAY, item("50.00", 2)));

        assertThat(result.getBaseAmount()).isEqualByComparingTo("100.00");
        assertThat(result.getDiscountAmount()).isEqualByComparingTo("40.00");
        assertThat(result.getFinalAmount()).isEqualByComparingTo("60.00");
        verify(strategy).calculateDiscount(any(BigDecimal.class), anyList(), any(DiscountContext.class));
    }

    @Test
    @DisplayName("discount > base produces final = 0, never negative")
    void finalAmountClampedToZero() {
        final DiscountContext ctx = percentageContext(DiscountType.BLACK_FRIDAY, new BigDecimal("40"));
        when(discountCampaignService.getActiveDiscountContext(DiscountType.BLACK_FRIDAY)).thenReturn(ctx);
        when(discountStrategyFactory.getStrategy(DiscountCalculationType.PERCENTAGE)).thenReturn(strategy);
        when(strategy.calculateDiscount(any(BigDecimal.class), anyList(), any(DiscountContext.class)))
                .thenReturn(new BigDecimal("20.00"));

        final PriceCalculationResultDto result = service.calculate(
                request(DiscountType.BLACK_FRIDAY, item("10.00", 1)));

        assertThat(result.getFinalAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("DiscountCampaignService throws (disabled / out of window) propagates without invoking strategy")
    void inactiveCampaignPropagates() {
        when(discountCampaignService.getActiveDiscountContext(DiscountType.BLACK_FRIDAY))
                .thenThrow(new DiscountTypeNotActiveException("Discount BLACK_FRIDAY is disabled"));

        assertThatThrownBy(() -> service.calculate(request(DiscountType.BLACK_FRIDAY, item("10.00", 1))))
                .isInstanceOf(DiscountTypeNotActiveException.class)
                .hasMessageContaining("BLACK_FRIDAY");

        verify(discountStrategyFactory, never()).getStrategy(any());
    }

    @Test
    @DisplayName("Active context but no strategy wired for calc type throws DiscountTypeNotActiveException")
    void missingStrategyThrows() {
        final DiscountContext ctx = percentageContext(DiscountType.WINTER_SALES, new BigDecimal("15"));
        when(discountCampaignService.getActiveDiscountContext(DiscountType.WINTER_SALES)).thenReturn(ctx);
        when(discountStrategyFactory.getStrategy(DiscountCalculationType.PERCENTAGE)).thenReturn(null);

        assertThatThrownBy(() -> service.calculate(request(DiscountType.WINTER_SALES, item("10.00", 1))))
                .isInstanceOf(DiscountTypeNotActiveException.class)
                .hasMessageContaining("No DiscountStrategy wired");
    }

    @Test
    @DisplayName("baseAmount < minOrderAmount: discount = 0, strategy not called")
    void belowMinOrderSkipsDiscount() {
        final DiscountContext ctx = DiscountContext.builder()
                .discountType(DiscountType.BLACK_FRIDAY)
                .calculationType(DiscountCalculationType.PERCENTAGE)
                .percentage(new BigDecimal("40"))
                .minOrderAmount(new BigDecimal("100.00"))
                .build();
        when(discountCampaignService.getActiveDiscountContext(DiscountType.BLACK_FRIDAY)).thenReturn(ctx);

        final PriceCalculationResultDto result = service.calculate(
                request(DiscountType.BLACK_FRIDAY, item("10.00", 1))); // base = 10, below min 100

        assertThat(result.getBaseAmount()).isEqualByComparingTo("10.00");
        assertThat(result.getDiscountAmount()).isEqualByComparingTo("0.00");
        assertThat(result.getFinalAmount()).isEqualByComparingTo("10.00");
        verify(discountStrategyFactory, never()).getStrategy(any());
    }

    @Test
    @DisplayName("baseAmount >= minOrderAmount: discount applies normally")
    void atMinOrderAppliesDiscount() {
        final DiscountContext ctx = DiscountContext.builder()
                .discountType(DiscountType.BLACK_FRIDAY)
                .calculationType(DiscountCalculationType.PERCENTAGE)
                .percentage(new BigDecimal("40"))
                .minOrderAmount(new BigDecimal("100.00"))
                .build();
        when(discountCampaignService.getActiveDiscountContext(DiscountType.BLACK_FRIDAY)).thenReturn(ctx);
        when(discountStrategyFactory.getStrategy(DiscountCalculationType.PERCENTAGE)).thenReturn(strategy);
        when(strategy.calculateDiscount(any(BigDecimal.class), anyList(), any(DiscountContext.class)))
                .thenReturn(new BigDecimal("40.00"));

        final PriceCalculationResultDto result = service.calculate(
                request(DiscountType.BLACK_FRIDAY, item("100.00", 1))); // base = 100, equals min

        assertThat(result.getDiscountAmount()).isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("baseAmount rounded HALF_UP to 2 dp")
    void baseAmountScaleIsTwo() {
        when(discountCampaignService.getActiveDiscountContext(DiscountType.NO_DISCOUNT))
                .thenReturn(noneContext(DiscountType.NO_DISCOUNT));

        final PriceCalculationResultDto result = service.calculate(
                request(DiscountType.NO_DISCOUNT, item("3.333", 3)));

        assertThat(result.getBaseAmount().scale()).isEqualTo(2);
        assertThat(result.getFinalAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("currency from request is preserved on result and asFinalMoney()")
    void currencyIsPreserved() {
        when(discountCampaignService.getActiveDiscountContext(DiscountType.NO_DISCOUNT))
                .thenReturn(noneContext(DiscountType.NO_DISCOUNT));

        final PriceCalculationResultDto result = service.calculate(
                PriceCalculationRequestDto.builder()
                        .items(List.of(item("10.00", 1)))
                        .discountType(DiscountType.NO_DISCOUNT)
                        .currencyCode(CurrencyCode.USD)
                        .build());

        assertThat(result.getCurrencyCode()).isEqualTo(CurrencyCode.USD);
        assertThat(result.asFinalMoney().getCurrencyCode()).isEqualTo(CurrencyCode.USD);
        assertThat(result.asFinalMoney().getAmount()).isEqualByComparingTo("10.00");
    }
}
