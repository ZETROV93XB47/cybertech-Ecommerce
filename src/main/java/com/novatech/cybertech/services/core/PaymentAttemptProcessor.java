package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.data.PaymentAttemptResult;
import com.novatech.cybertech.entities.valueObjects.Money;

import java.util.UUID;

public interface PaymentAttemptProcessor {
    PaymentAttemptResult processPayment(UUID orderUuid, Money amount, String idempotencyKey);
}

