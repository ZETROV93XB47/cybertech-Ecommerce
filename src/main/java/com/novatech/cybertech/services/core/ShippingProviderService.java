package com.novatech.cybertech.services.core;

import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.entities.valueObjects.DeliveryWindow;

import java.math.BigDecimal;

public interface ShippingProviderService {
    BigDecimal calculateShippingCost(ShippingType shippingType);

    String deliver(String packageId, ShippingType shippingType);

    /** Indicative delivery time of this carrier for {@code shippingType} (1 to 3 business days). */
    DeliveryWindow getDeliveryWindow(ShippingType shippingType);
}
