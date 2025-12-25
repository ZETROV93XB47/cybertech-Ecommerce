package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.annotation.ShippingProviderHandler;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.services.core.ShippingProviderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Primary
@Service
@ShippingProviderHandler(ShippingProvider.DHL)
public class DHLShippingProviderService implements ShippingProviderService {

    @Override
    public BigDecimal calculateShippingCost(ShippingType shippingType) {
        return switch (shippingType) {
            case EXPRESS -> BigDecimal.valueOf(25L);
            case STANDARD -> BigDecimal.valueOf(15L);
        };
    }

    @Override
    public String deliver(String packageId, ShippingType shippingType) {
        return switch (shippingType) {
            case EXPRESS -> "DHL " + ShippingType.EXPRESS.name() + " delivery for " + packageId;
            case STANDARD -> "DHL " + ShippingType.STANDARD.name() + " delivery for " + packageId;
        };
    }
}
