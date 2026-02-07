package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.PaymentAttemptResult;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.PaymentAttemptEntity;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.entities.valueObjects.Money;
import com.novatech.cybertech.exceptions.PaymentAlreadyCompletedForThisOrderException;
import com.novatech.cybertech.factory.PaymentStrategyFactory;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.repositories.PaymentAttemptRepository;
import com.novatech.cybertech.services.core.PaymentAttemptProcessor;
import com.novatech.cybertech.services.core.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImp implements PaymentService {

    private final PaymentAttemptRepository attemptRepository;
    private final PaymentStrategyFactory paymentStrategyFactory;

    @Transactional
    public PaymentAttemptEntity processPayment(OrderEntity order, PaymentType paymentType, Money amount, String idempotencyKey) {

        // Idempotence (retry même requête)
        final Optional<PaymentAttemptEntity> existing = attemptRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            if(existing.get().getStatus() == PaymentAttemptStatus.SUCCESS) throw new PaymentAlreadyCompletedForThisOrderException("Payment already completed for this order");
        }

        // Crée attempt
        PaymentAttemptEntity attempt = PaymentAttemptEntity.builder()
                .orderEntity(order)
                .amount(amount)
                .paymentType(paymentType)
                .status(PaymentAttemptStatus.CREATED)
                .idempotencyKey(idempotencyKey)
                .build();

        attempt = attemptRepository.save(attempt);

        // Appel provider via Strategy
        attempt.setStatus(PaymentAttemptStatus.PROCESSING);

        PaymentAttemptProcessor processor = paymentStrategyFactory.getServiceFromPaymentType(paymentType);

        // Idée: ton processor retourne un résultat (au lieu de muter une entity PaymentEntity)
        PaymentAttemptResult result = processor.processPayment(order.getUuid(), amount, idempotencyKey);

        attempt.setStatus(result.status());
        attempt.setProviderRef(result.providerRef());

        return attemptRepository.save(attempt);
    }
}
