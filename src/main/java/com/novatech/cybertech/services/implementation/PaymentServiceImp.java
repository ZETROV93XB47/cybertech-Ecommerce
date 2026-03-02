package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.PaymentAttemptResult;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.PaymentAttemptEntity;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.entities.enums.TransactionType;
import com.novatech.cybertech.entities.valueObjects.Money;
import com.novatech.cybertech.exceptions.PaymentAlreadyCompletedForThisOrderException;
import com.novatech.cybertech.exceptions.PaymentNotFoundException;
import com.novatech.cybertech.factory.PaymentStrategyFactory;
import com.novatech.cybertech.repositories.PaymentAttemptRepository;
import com.novatech.cybertech.services.core.PaymentAttemptProcessor;
import com.novatech.cybertech.services.core.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImp implements PaymentService {

    private final PaymentStrategyFactory paymentStrategyFactory;
    private final PaymentAttemptRepository paymentAttemptRepository;

    @Transactional
    public PaymentAttemptEntity processPayment(OrderEntity order, PaymentType paymentType, Money amount, String idempotencyKey) {

        // Idempotence (retry même requête)
        final Optional<PaymentAttemptEntity> existing = paymentAttemptRepository.findByIdempotencyKey(idempotencyKey);

        log.info("existing :: {}", existing);

        if (existing.isPresent()) {
            if (existing.get().getStatus() == PaymentAttemptStatus.SUCCESS) {
                log.info("Payment already completed for this order");
                throw new PaymentAlreadyCompletedForThisOrderException("Payment already completed for this order");
            }
        }

        // Crée attempt
        PaymentAttemptEntity attempt = PaymentAttemptEntity.builder()
                .orderEntity(order)
                .amount(amount)
                .paymentType(paymentType)
                .transactionType(TransactionType.PAYMENT)
                .status(PaymentAttemptStatus.CREATED)
                .idempotencyKey(idempotencyKey)
                .build();

        attempt = paymentAttemptRepository.save(attempt);

        // Appel provider via Strategy
        attempt.setStatus(PaymentAttemptStatus.PROCESSING);

        PaymentAttemptProcessor processor = paymentStrategyFactory.getServiceFromPaymentType(paymentType);

        // Idée: ton processor retourne un résultat (au lieu de muter une entity PaymentEntity)
        PaymentAttemptResult result = processor.processPayment(order.getUuid(), amount, idempotencyKey);

        attempt.setStatus(result.status());
        attempt.setStripePaymentID(result.stripePaymentID());
        log.info("saved Payment id : {}", result.stripePaymentID());

        return paymentAttemptRepository.save(attempt);
    }

    @Transactional
    public PaymentAttemptEntity refund(OrderEntity order, PaymentType paymentType, Money amount, String idempotencyKey) {
        log.info("In refund method for order: {}, amount: {}", order.getUuid(), amount);

        final PaymentAttemptEntity paymentAttemptEntity = paymentAttemptRepository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> new PaymentNotFoundException("No payment attempt found for idempotency key: " + idempotencyKey));
        final String stripePaymentID = paymentAttemptEntity.getStripePaymentID();

        // Crée attempt de remboursement
        PaymentAttemptEntity attempt = PaymentAttemptEntity.builder()
                .orderEntity(order)
                .amount(amount)
                .paymentType(paymentType)
                .transactionType(TransactionType.REFUND)
                .status(PaymentAttemptStatus.CREATED)
                .idempotencyKey(idempotencyKey)
                .build();

        attempt = paymentAttemptRepository.save(attempt);
        attempt.setStatus(PaymentAttemptStatus.PROCESSING);

        log.info("Refund attempt created with ID: {}", attempt.getId());

        PaymentAttemptProcessor processor = paymentStrategyFactory.getServiceFromPaymentType(paymentType);

        log.info("Calling payment processor for refund...");
        PaymentAttemptResult result = processor.refund(order.getUuid(), amount, idempotencyKey, stripePaymentID);
        log.info("Payment processor response: status={}, ref={}", result.status(), result.stripePaymentID());

        attempt.setStatus(result.status());
        attempt.setStripePaymentID(result.stripePaymentID());

        return paymentAttemptRepository.save(attempt);
    }
}
