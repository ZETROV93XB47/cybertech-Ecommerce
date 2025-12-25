package com.novatech.cybertech.services.core;

import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.enums.PaymentType;

import java.math.BigDecimal;

public interface PaymentService {
    PaymentEntity processPayment(final PaymentType paymentType, final BigDecimal paymentAmount);
}
