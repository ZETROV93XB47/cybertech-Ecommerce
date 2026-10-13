package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.response.shipping.ShippingOptionResponseDto;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.entities.valueObjects.CurrencyCode;
import com.novatech.cybertech.factory.ShippingProviderStrategyFactory;
import com.novatech.cybertech.services.core.OrderPriceCalculationService;
import com.novatech.cybertech.services.core.ShippingOptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * See {@link ShippingOptionService}. Costs come from
 * {@link OrderPriceCalculationService#calculateShippingCost} — the very method that prices an
 * order's shipping — so the list can never drift from what an order is actually charged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShippingOptionServiceImp implements ShippingOptionService {

    // Every order is priced in EUR today (see OrderCreationTransactionalDelegateImp).
    private static final String ORDER_CURRENCY = CurrencyCode.EUR.getCode();

    private final ShippingProviderStrategyFactory shippingProviderStrategyFactory;
    private final OrderPriceCalculationService orderPriceCalculationService;

    @Override
    public List<ShippingOptionResponseDto> getShippingOptions(final ShippingProvider provider) {
        final Stream<ShippingProvider> providers = provider == null ? Arrays.stream(ShippingProvider.values()) : Stream.of(provider);

        final List<ShippingOptionResponseDto> options = providers
                // A provider without a wired shipping service cannot be ordered with: not listed.
                .filter(p -> shippingProviderStrategyFactory.getStrategy(p) != null)
                .flatMap(p -> Arrays.stream(ShippingType.values()).map(type -> ShippingOptionResponseDto.builder()
                        .provider(p)
                        .type(type)
                        .cost(orderPriceCalculationService.calculateShippingCost(p, type))
                        .currency(ORDER_CURRENCY)
                        .build()))
                .sorted(Comparator.comparing((ShippingOptionResponseDto o) -> o.getProvider().name())
                        .thenComparing(ShippingOptionResponseDto::getCost))
                .toList();

        log.debug("{} shipping option(s) listed (provider filter: {})", options.size(), provider);
        return options;
    }
}
