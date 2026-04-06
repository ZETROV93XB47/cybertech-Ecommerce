package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.NotificationPayload;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ShippingConfirmationPayload implements NotificationPayload {
    private UUID orderUuid;
    private String userName;
    private ShippingType shippingType;
    private ShippingProvider shippingProvider;
}
