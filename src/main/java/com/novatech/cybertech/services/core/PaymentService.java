package com.novatech.cybertech.services.core;

import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.entities.valueObjects.Money;

public interface PaymentService {
    PaymentEntity processPayment(OrderEntity order, PaymentType paymentType, Money amount, String idempotencyKey);
    PaymentEntity refund(OrderEntity order, PaymentType paymentType, Money amount, String idempotencyKey);
}
