package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.config.ActiveDiscountsProperties;
import com.novatech.cybertech.dto.request.order.OrderItemPriceDto;
import com.novatech.cybertech.dto.request.order.PriceCalculationRequestDto;
import com.novatech.cybertech.dto.response.order.PriceCalculationResultDto;
import com.novatech.cybertech.entities.enums.DiscountType;
import com.novatech.cybertech.entities.valueObjects.CurrencyCode;
import com.novatech.cybertech.exceptions.DiscountTypeNotActiveException;
import com.novatech.cybertech.factory.DiscountStrategyFactory;
import com.novatech.cybertech.strategy.discount.DiscountStrategy;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPriceCalculationServiceImpTest {

    @Mock
    private DiscountStrategyFactory discountStrategyFactory;

    @Mock
    private DiscountStrategy blackFridayStrategy;

    private ActiveDiscountsProperties activeDiscountsProperties;

    @InjectMocks
    private OrderPriceCalculationServiceImp service;

    @BeforeEach
    void setUp() {
        activeDiscountsProperties = new ActiveDiscountsProperties();
        // noDiscount=true by default; explicitly enable BLACK_FRIDAY for most tests
        activeDiscountsProperties.setBlackFriday(true);
        service = new OrderPriceCalculationServiceImp(discountStrategyFactory, activeDiscountsProperties);
    }

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

    @Test
    @DisplayName("NO_DISCOUNT: base = sum(unitPrice * quantity), discount = 0, final = base")
    void noDiscountReturnsBaseAsFinal() {
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
    @DisplayName("BLACK_FRIDAY: delegates to the strategy, subtracts the discount from base")
    void blackFridayDelegatesToStrategyAndSubtracts() {
        when(discountStrategyFactory.getStrategy(DiscountType.BLACK_FRIDAY)).thenReturn(blackFridayStrategy);
        when(blackFridayStrategy.calculateDiscount(new BigDecimal("100.00"))).thenReturn(new BigDecimal("40.00"));

        final PriceCalculationResultDto result = service.calculate(
                request(DiscountType.BLACK_FRIDAY, item("50.00", 2)));

        assertThat(result.getBaseAmount()).isEqualByComparingTo("100.00");
        assertThat(result.getDiscountAmount()).isEqualByComparingTo("40.00");
        assertThat(result.getFinalAmount()).isEqualByComparingTo("60.00");
        assertThat(result.getDiscountType()).isEqualTo(DiscountType.BLACK_FRIDAY);
    }

    @Test
    @DisplayName("discount > base never produces a negative finalAmount — clamped to zero")
    void finalAmountClampedToZero() {
        when(discountStrategyFactory.getStrategy(DiscountType.BLACK_FRIDAY)).thenReturn(blackFridayStrategy);
        when(blackFridayStrategy.calculateDiscount(new BigDecimal("10.00"))).thenReturn(new BigDecimal("20.00"));

        final PriceCalculationResultDto result = service.calculate(
                request(DiscountType.BLACK_FRIDAY, item("10.00", 1)));

        assertThat(result.getFinalAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("inactive DiscountType throws DiscountTypeNotActiveException without touching the factory")
    void inactiveDiscountTypeThrows() {
        // Only NO_DISCOUNT active (disable BLACK_FRIDAY which was set in setUp)
        activeDiscountsProperties.setBlackFriday(false);
        service = new OrderPriceCalculationServiceImp(discountStrategyFactory, activeDiscountsProperties);

        assertThatThrownBy(() -> service.calculate(
                request(DiscountType.BLACK_FRIDAY, item("10.00", 1))))
                .isInstanceOf(DiscountTypeNotActiveException.class)
                .hasMessageContaining("BLACK_FRIDAY");

        verify(discountStrategyFactory, never()).getStrategy(any());
    }

    @Test
    @DisplayName("active type with no wired strategy throws DiscountTypeNotActiveException (wiring gap)")
    void activeTypeWithMissingStrategyThrows() {
        // NO_DISCOUNT + WINTER_SALES active (blackFriday was set true in setUp, so disable it)
        activeDiscountsProperties.setBlackFriday(false);
        activeDiscountsProperties.setWinterSales(true);
        service = new OrderPriceCalculationServiceImp(discountStrategyFactory, activeDiscountsProperties);
        when(discountStrategyFactory.getStrategy(DiscountType.WINTER_SALES)).thenReturn(null);

        assertThatThrownBy(() -> service.calculate(
                request(DiscountType.WINTER_SALES, item("10.00", 1))))
                .isInstanceOf(DiscountTypeNotActiveException.class)
                .hasMessageContaining("No DiscountStrategy is wired");
    }

    @Test
    @DisplayName("null DiscountType throws DiscountTypeNotActiveException (never reaches the factory's null-guard)")
    void nullDiscountTypeThrows() {
        assertThatThrownBy(() -> service.calculate(request(null, item("10.00", 1))))
                .isInstanceOf(DiscountTypeNotActiveException.class);
    }

    @Test
    @DisplayName("base amount is rounded HALF_UP to 2 dp")
    void baseAmountScaleIsTwo() {
        final PriceCalculationResultDto result = service.calculate(
                request(DiscountType.NO_DISCOUNT, item("3.333", 3)));

        assertThat(result.getBaseAmount().scale()).isEqualTo(2);
        assertThat(result.getFinalAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("currency from request is preserved on the result and in asFinalMoney()")
    void currencyIsPreserved() {
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
