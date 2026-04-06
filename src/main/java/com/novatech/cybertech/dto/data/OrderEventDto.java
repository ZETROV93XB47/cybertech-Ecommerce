package com.novatech.cybertech.dto.data;

import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class OrderEventDto implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private UUID orderUuid;
    private BigDecimal totalAmount;
    private OrderStatus orderStatus;
    private ShippingType shippingType;
    private UserContactDto userContactDto;
    private ShippingProvider shippingProvider;
    private PaymentAttemptStatus paymentAttemptStatus;
    //private Map<UUID, Integer> productsByQuantityMap;
}