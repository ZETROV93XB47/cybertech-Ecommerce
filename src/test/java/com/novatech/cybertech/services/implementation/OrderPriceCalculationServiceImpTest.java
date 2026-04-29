package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.DiscountContext;
import com.novatech.cybertech.dto.request.order.OrderItemPriceDto;
import com.novatech.cybertech.dto.request.order.PriceCalculationRequestDto;
import com.novatech.cybertech.dto.response.order.PriceCalculationResultDto;
import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import com.novatech.cybertech.entities.enums.DiscountType;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.entities.valueObjects.CurrencyCode;
import com.novatech.cybertech.exceptions.DiscountTypeNotActiveException;
import com.novatech.cybertech.exceptions.NoStrategyFoundForProcessingTheRequest;
import com.novatech.cybertech.factory.DiscountStrategyFactory;
import com.novatech.cybertech.factory.ShippingProviderStrategyFactory;
import com.novatech.cybertech.services.core.DiscountCampaignService;
import com.novatech.cybertech.services.core.ShippingProviderService;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPriceCalculationServiceImpTest {

    /** Default shipping cost returned by the mocked ShippingProviderService for DHL+STANDARD. */
    private static final BigDecimal DEFAULT_SHIPPING_COST = new BigDecimal("15.00");

    @Mock private DiscountStrategyFactory discountStrategyFactory;
    @Mock private DiscountCampaignService discountCampaignService;
    @Mock private DiscountStrategy strategy;
    @Mock private ShippingProviderStrategyFactory shippingProviderStrategyFactory;
    @Mock private ShippingProviderService shippingProviderService;

    @InjectMocks
    private OrderPriceCalculationServiceImp service;

    @BeforeEach
    void wireDefaultShippingStub() {
        // Default: DHL+STANDARD resolves and returns a fixed 15.00 shipping cost. Tests that exercise
        // the missing-strategy path or want a different cost override these stubs locally.
        lenient().when(shippingProviderStrategyFactory.getStrategy(any(ShippingProvider.class)))
                .thenReturn(shippingProviderService);
        lenient().when(shippingProviderService.calculateShippingCost(any(ShippingType.class)))
                .thenReturn(DEFAULT_SHIPPING_COST);
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
                .shippingProvider(ShippingProvider.DHL)
                .shippingType(ShippingType.STANDARD)
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
    @DisplayName("NO_DISCOUNT short-circuits: campaign service NOT consulted, factory NOT consulted; shipping cost folded into final")
    void noneSkipsStrategy() {
        final PriceCalculationResultDto result = service.calculate(
                request(DiscountType.NO_DISCOUNT, item("10.00", 2), item("5.00", 3)));

        assertThat(result.getBaseAmount()).isEqualByComparingTo("35.00");
        assertThat(result.getDiscountAmount()).isEqualByComparingTo("0.00");
        assertThat(result.getShippingCost()).isEqualByComparingTo(DEFAULT_SHIPPING_COST);
        // base 35 + shipping 15 = 50
        assertThat(result.getFinalAmount()).isEqualByComparingTo("50.00");
        assertThat(result.getDiscountType()).isEqualTo(DiscountType.NO_DISCOUNT);
        assertThat(result.getCurrencyCode()).isEqualTo(CurrencyCode.EUR);
        verify(discountCampaignService, never()).getActiveDiscountContext(any());
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
        // base 100 - discount 40 + shipping 15 = 75
        assertThat(result.getFinalAmount()).isEqualByComparingTo("75.00");
        verify(strategy).calculateDiscount(any(BigDecimal.class), anyList(), any(DiscountContext.class));
    }

    @Test
    @DisplayName("discount > base clamps discounted subtotal at 0, but shipping is still added on top")
    void finalAmountClampedToZero() {
        final DiscountContext ctx = percentageContext(DiscountType.BLACK_FRIDAY, new BigDecimal("40"));
        when(discountCampaignService.getActiveDiscountContext(DiscountType.BLACK_FRIDAY)).thenReturn(ctx);
        when(discountStrategyFactory.getStrategy(DiscountCalculationType.PERCENTAGE)).thenReturn(strategy);
        when(strategy.calculateDiscount(any(BigDecimal.class), anyList(), any(DiscountContext.class)))
                .thenReturn(new BigDecimal("20.00"));

        final PriceCalculationResultDto result = service.calculate(
                request(DiscountType.BLACK_FRIDAY, item("10.00", 1)));

        // max(10 - 20, 0) + 15 = 0 + 15 = 15
        assertThat(result.getFinalAmount()).isEqualByComparingTo("15.00");
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
        // base 10 + shipping 15 = 25 (no discount applied because below min)
        assertThat(result.getFinalAmount()).isEqualByComparingTo("25.00");
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
        final PriceCalculationResultDto result = service.calculate(
                request(DiscountType.NO_DISCOUNT, item("3.333", 3)));

        assertThat(result.getBaseAmount().scale()).isEqualTo(2);
        // base 9.999 → rounded to 10.00 (NO_DISCOUNT path uses .setScale(HALF_UP)) + shipping 15 = 25.00
        assertThat(result.getFinalAmount()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("currency from request is preserved on result and asFinalMoney()")
    void currencyIsPreserved() {
        final PriceCalculationResultDto result = service.calculate(
                PriceCalculationRequestDto.builder()
                        .items(List.of(item("10.00", 1)))
                        .discountType(DiscountType.NO_DISCOUNT)
                        .currencyCode(CurrencyCode.USD)
                        .shippingProvider(ShippingProvider.DHL)
                        .shippingType(ShippingType.STANDARD)
                        .build());

        assertThat(result.getCurrencyCode()).isEqualTo(CurrencyCode.USD);
        assertThat(result.asFinalMoney().getCurrencyCode()).isEqualTo(CurrencyCode.USD);
        // base 10 + shipping 15 = 25
        assertThat(result.asFinalMoney().getAmount()).isEqualByComparingTo("25.00");
    }

    // -----------------------------------------------------------------
    // Shipping integration tests (FIX(SHIPPING-INT))
    // -----------------------------------------------------------------

    @Test
    @DisplayName("shipping cost is added to finalAmount and surfaced via getShippingCost()")
    void shippingCostAddedToFinalAmount() {
        // Override the default 15.00 stub with an explicit 7.50 to make the assertion crisp.
        when(shippingProviderService.calculateShippingCost(ShippingType.EXPRESS))
                .thenReturn(new BigDecimal("7.50"));

        final PriceCalculationResultDto result = service.calculate(
                PriceCalculationRequestDto.builder()
                        .items(List.of(item("20.00", 1)))
                        .discountType(DiscountType.NO_DISCOUNT)
                        .currencyCode(CurrencyCode.EUR)
                        .shippingProvider(ShippingProvider.FEDEX)
                        .shippingType(ShippingType.EXPRESS)
                        .build());

        assertThat(result.getBaseAmount()).isEqualByComparingTo("20.00");
        assertThat(result.getShippingCost()).isEqualByComparingTo("7.50");
        // base 20 + shipping 7.50 = 27.50
        assertThat(result.getFinalAmount()).isEqualByComparingTo("27.50");
        verify(shippingProviderStrategyFactory).getStrategy(ShippingProvider.FEDEX);
        verify(shippingProviderService).calculateShippingCost(ShippingType.EXPRESS);
    }

    @Test
    @DisplayName("missing ShippingProviderService for the requested provider throws NoStrategyFoundForProcessingTheRequest")
    void noShippingStrategyThrowsNoStrategyFoundForProcessingTheRequest() {
        when(shippingProviderStrategyFactory.getStrategy(ShippingProvider.DHL)).thenReturn(null);

        assertThatThrownBy(() -> service.calculate(
                request(DiscountType.NO_DISCOUNT, item("10.00", 1))))
                .isInstanceOf(NoStrategyFoundForProcessingTheRequest.class)
                .hasMessageContaining("DHL");
    }

    @Test
    @DisplayName("NO_DISCOUNT path: shipping is added exactly once to finalAmount (no double-counting)")
    void shippingCostNotDoubleCountedOnNO_DISCOUNT() {
        final PriceCalculationResultDto result = service.calculate(
                request(DiscountType.NO_DISCOUNT, item("100.00", 1)));

        assertThat(result.getBaseAmount()).isEqualByComparingTo("100.00");
        assertThat(result.getShippingCost()).isEqualByComparingTo(DEFAULT_SHIPPING_COST);
        // base + shipping (NOT base + shipping + shipping); the NO_DISCOUNT branch must add shipping
        // exactly once just like the discounted branch.
        assertThat(result.getFinalAmount()).isEqualByComparingTo("115.00");
        verify(shippingProviderStrategyFactory).getStrategy(ShippingProvider.DHL);
        verify(shippingProviderService).calculateShippingCost(ShippingType.STANDARD);
    }
}
