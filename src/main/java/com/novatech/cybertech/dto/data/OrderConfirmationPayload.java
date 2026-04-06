package com.novatech.cybertech.dto.data;


import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class OrderConfirmationPayload implements NotificationPayload {

    private UUID orderUuid;
    private BigDecimal totalAmount;
    private OrderStatus orderStatus;
    private UserContactDto userContactDto;
    private PaymentAttemptStatus paymentAttemptStatus;
}
