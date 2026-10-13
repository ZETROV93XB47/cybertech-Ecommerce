package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.request.order.OrderItemPriceDto;
import com.novatech.cybertech.dto.request.order.PriceCalculationRequestDto;
import com.novatech.cybertech.dto.response.shipping.ShippingOptionResponseDto;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.entities.valueObjects.CurrencyCode;
import com.novatech.cybertech.factory.DiscountStrategyFactory;
import com.novatech.cybertech.factory.ShippingProviderStrategyFactory;
import com.novatech.cybertech.services.core.DiscountCampaignService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.NO_DISCOUNT_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * {@link ShippingOptionServiceImp} wired on the REAL {@link OrderPriceCalculationServiceImp} and the
 * real DHL / FedEx services, so the listed costs are checked against what an order is charged.
 */
@ExtendWith(MockitoExtension.class)
class ShippingOptionServiceImpTest {

    @Mock private DiscountCampaignService discountCampaignService;

    private OrderPriceCalculationServiceImp priceService;
    private ShippingOptionServiceImp service;

    @BeforeEach
    void setUp() {
        final ShippingProviderStrategyFactory shippingFactory = new ShippingProviderStrategyFactory(Map.of(
                ShippingProvider.DHL, new DHLShippingProviderService(),
                ShippingProvider.FEDEX, new FedexShippingProviderService()));
        priceService = new OrderPriceCalculationServiceImp(new DiscountStrategyFactory(Map.of()), discountCampaignService, shippingFactory);
        service = new ShippingOptionServiceImp(shippingFactory, priceService);
    }

    @Test
    @DisplayName("no filter: one entry per (provider, type), sorted by provider then cost, in EUR with 2 decimals")
    void allOptions_sortedByProviderThenCost() {
        final List<ShippingOptionResponseDto> options = service.getShippingOptions(null);

        assertThat(options)
                .extracting(ShippingOptionResponseDto::getProvider, ShippingOptionResponseDto::getType, o -> o.getCost().toPlainString(), ShippingOptionResponseDto::getCurrency)
                .containsExactly(
                        tuple(ShippingProvider.DHL, ShippingType.STANDARD, "15.00", "EUR"),
                        tuple(ShippingProvider.DHL, ShippingType.EXPRESS, "25.00", "EUR"),
                        tuple(ShippingProvider.FEDEX, ShippingType.STANDARD, "15.00", "EUR"),
                        tuple(ShippingProvider.FEDEX, ShippingType.EXPRESS, "25.00", "EUR"));
    }

    @Test
    @DisplayName("provider filter: only that carrier's options")
    void providerFilter_onlyThatCarrier() {
        assertThat(service.getShippingOptions(ShippingProvider.FEDEX))
                .extracting(ShippingOptionResponseDto::getProvider)
                .containsOnly(ShippingProvider.FEDEX)
                .hasSize(ShippingType.values().length);
    }

    @Test
    @DisplayName("provider with no shipping service wired: not listed (it cannot be ordered with)")
    void providerWithoutService_notListed() {
        final ShippingProviderStrategyFactory dhlOnly = new ShippingProviderStrategyFactory(Map.of(ShippingProvider.DHL, new DHLShippingProviderService()));
        final ShippingOptionServiceImp dhlOnlyService = new ShippingOptionServiceImp(dhlOnly,
                new OrderPriceCalculationServiceImp(new DiscountStrategyFactory(Map.of()), discountCampaignService, dhlOnly));

        assertThat(dhlOnlyService.getShippingOptions(ShippingProvider.FEDEX)).isEmpty();
        assertThat(dhlOnlyService.getShippingOptions(null)).extracting(ShippingOptionResponseDto::getProvider).containsOnly(ShippingProvider.DHL);
    }

    @Test
    @DisplayName("every listed cost is exactly the shippingCost OrderPriceCalculationService applies to an order")
    void listedCost_equalsCostAppliedToOrder() {
        service.getShippingOptions(null).forEach(option -> {
            final PriceCalculationRequestDto order = PriceCalculationRequestDto.builder()
                    .items(List.of(OrderItemPriceDto.builder().productUuid(UUID.randomUUID()).unitPrice(new BigDecimal("42.00")).quantity(1).build()))
                    .discountKey(NO_DISCOUNT_KEY)
                    .currencyCode(CurrencyCode.EUR)
                    .shippingProvider(option.getProvider())
                    .shippingType(option.getType())
                    .build();

            assertThat(option.getCost())
                    .as("%s %s", option.getProvider(), option.getType())
                    .isEqualTo(priceService.calculate(order).getShippingCost());
        });
    }
}
