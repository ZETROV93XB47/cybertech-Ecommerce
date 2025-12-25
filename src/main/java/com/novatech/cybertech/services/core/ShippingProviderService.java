package com.novatech.cybertech.services.core;

import com.novatech.cybertech.entities.enums.ShippingType;

import java.math.BigDecimal;

public interface ShippingProviderService {
    BigDecimal calculateShippingCost(ShippingType shippingType);

    String deliver(String packageId, ShippingType shippingType);
}
