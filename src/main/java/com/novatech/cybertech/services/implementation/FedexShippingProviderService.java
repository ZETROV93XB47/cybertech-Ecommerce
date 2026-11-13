package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.annotation.ShippingProviderHandler;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.entities.valueObjects.DeliveryWindow;
import com.novatech.cybertech.services.core.ShippingProviderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalTime;

@Slf4j
@Service
@ShippingProviderHandler(ShippingProvider.FEDEX)
public class FedexShippingProviderService implements ShippingProviderService {

    private static final LocalTime EXPRESS_CUTOFF_TIME = LocalTime.of(15, 0);

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
            case EXPRESS -> "FEDEX " + ShippingType.EXPRESS.name() + " delivery for " + packageId;
            case STANDARD -> "FEDEX " + ShippingType.STANDARD.name() + " delivery for " + packageId;
        };
    }

    // Indicative only: EXPRESS is next business day for an order placed by 15:00.
    @Override
    public DeliveryWindow getDeliveryWindow(ShippingType shippingType) {
        return switch (shippingType) {
            case EXPRESS -> new DeliveryWindow(1, 1, EXPRESS_CUTOFF_TIME);
            case STANDARD -> new DeliveryWindow(1, 2, null);
        };
    }
}
