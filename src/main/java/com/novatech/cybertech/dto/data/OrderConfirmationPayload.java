package com.novatech.cybertech.dto.data;


import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderConfirmationPayload implements NotificationPayload {

    private UUID orderUuid;
    private BigDecimal totalAmount;
    private OrderStatus orderStatus;
    private UserContactDto userContactDto;
    private PaymentAttemptStatus paymentAttemptStatus;
}
