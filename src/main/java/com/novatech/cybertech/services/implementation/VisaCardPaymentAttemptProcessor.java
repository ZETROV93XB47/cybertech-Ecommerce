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

import java.util.Random;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
@PaymentTypeHandler(PaymentType.VISA)
public class VisaCardPaymentAttemptProcessor implements PaymentAttemptProcessor {

    private final Random random = new Random();

    @Override
    public PaymentAttemptResult processPayment(UUID orderUuid, Money amount, String idempotencyKey) {
        log.info("Calling provider (simulated) for order={}, amount={}, idemKey={}", orderUuid, amount, idempotencyKey);
        return simulateProviderResponse(orderUuid);
    }

    @Override
    public PaymentAttemptResult refund(UUID orderUuid, Money amount, String idempotencyKey) {
        log.info("Calling provider (simulated refund) for order={}, amount={}, idemKey={}", orderUuid, amount, idempotencyKey);
        return new PaymentAttemptResult(PaymentAttemptStatus.SUCCESS, "prov_mc_" + orderUuid + "_" + System.currentTimeMillis());
    }

    private PaymentAttemptResult simulateProviderResponse(UUID orderUuid) {
        int chance = random.nextInt(10); // Génère un nombre entre 0 et 9
        PaymentAttemptStatus status;

        if (chance <= 5) { // 0 à 5 (60%)
            status = PaymentAttemptStatus.SUCCESS;
        } else if (chance <= 7) { // 6 à 7 (20%)
            status = PaymentAttemptStatus.FAILED;
        } else { // 8 à 9 (20%)
            status = PaymentAttemptStatus.PROCESSING;
        }

        return new PaymentAttemptResult(status, "prov_visa_" + orderUuid + "_" + System.currentTimeMillis());
    }
}