package com.novatech.cybertech.services.core;

import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.PaymentAttemptEntity;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.entities.valueObjects.Money;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentService {
    PaymentAttemptEntity processPayment(OrderEntity order, PaymentType paymentType, Money amount, String idempotencyKey);
    PaymentAttemptEntity refund(OrderEntity order, PaymentType paymentType, Money amount, String idempotencyKey);
}
