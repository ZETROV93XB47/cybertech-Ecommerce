package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.annotation.PaymentTypeHandler;
import com.novatech.cybertech.dto.data.PaymentAttemptResult;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.entities.valueObjects.Money;
import com.novatech.cybertech.services.core.PaymentAttemptProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
@PaymentTypeHandler(PaymentType.VISA)
public class VisaCardPaymentAttemptProcessor implements PaymentAttemptProcessor {

    @Override
    public PaymentAttemptResult processPayment(UUID orderUuid, Money amount, String idempotencyKey) {
        log.info("Calling provider (simulated) for order={}, amount={}, idemKey={}", orderUuid, amount, idempotencyKey);
        // no 3DS => sync success/failed
        return new PaymentAttemptResult(PaymentAttemptStatus.SUCCESS, "prov_" + orderUuid);
    }
}