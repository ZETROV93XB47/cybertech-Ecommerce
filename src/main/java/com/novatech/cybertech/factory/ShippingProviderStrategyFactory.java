package com.novatech.cybertech.factory;

import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.services.core.ShippingProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ShippingProviderStrategyFactory {

    private final Map<ShippingProvider, ShippingProviderService> strategyMap;

    public ShippingProviderService getStrategy(final ShippingProvider shippingProvider) {
        return strategyMap.get(shippingProvider);
    }
}
