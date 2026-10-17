package com.novatech.cybertech.dto.data;

import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import lombok.*;

@With
@Getter
@Builder
@ToString
@EqualsAndHashCode
public class ShippingContext {
    private final String packageId;
    private final UserContactDto user;
    private final ShippingType shippingType;
    private final ShippingProvider shippingProvider;
}
