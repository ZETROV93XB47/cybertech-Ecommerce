package com.novatech.cybertech.services.core;

import com.novatech.cybertech.entities.PaymentEntity;

public interface PaymentProcessor {
    PaymentEntity processPayment(PaymentEntity payment);
}

