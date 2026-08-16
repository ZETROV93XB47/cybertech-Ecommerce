package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.PaymentAttemptResult;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.entities.enums.TransactionType;
import com.novatech.cybertech.entities.valueObjects.Money;
import com.novatech.cybertech.exceptions.PaymentAlreadyCompletedForThisOrderException;
import com.novatech.cybertech.exceptions.PaymentNotFoundException;
import com.novatech.cybertech.factory.PaymentStrategyFactory;
import com.novatech.cybertech.repositories.PaymentAttemptRepository;
import com.novatech.cybertech.services.core.IdempotencyKeyServiceGenerator;
import com.novatech.cybertech.services.core.PaymentAttemptProcessor;
import com.novatech.cybertech.services.core.PaymentService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImp implements PaymentService {

    private static final String SUCCESS_OUTCOME = "success";
    private static final String FAILURE_OUTCOME = "failure";

    private final PaymentStrategyFactory paymentStrategyFactory;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final IdempotencyKeyServiceGenerator idempotencyKeyService;

    /**
     * Field-injected (not added to the {@code @RequiredArgsConstructor} constructor) so the
     * existing {@code @InjectMocks}-based {@code PaymentServiceImpTest} doesn't need to mock a
     * {@link MeterRegistry} — same pattern as {@code OrderManagementServiceImp#transactionManager}.
     * Backs {@code cybertech_payments_total{outcome=...}}, already queried by
     * {@code cybertech-overview.json} (Grafana) and the payment-success-rate alert rule
     * (prometheus-chart), but never actually emitted until now.
     */
    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    @Transactional
    public PaymentEntity processPayment(OrderEntity order, PaymentType paymentType, Money amount, String idempotencyKey) {

        // Idempotence (retry même requête)
        final Optional<PaymentEntity> existing = paymentAttemptRepository.findByIdempotencyKey(idempotencyKey);

        log.info("existing :: {}", existing);

        if (existing.isPresent()) {
            if (existing.get().getStatus() == PaymentAttemptStatus.SUCCESS) {
                log.info("Payment already completed for this order");
                throw new PaymentAlreadyCompletedForThisOrderException("Payment already completed for this order");
            }
        }

        // Crée attempt
        PaymentEntity attempt = PaymentEntity.builder()
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

        if (meterRegistry != null) {
            final String outcome = result.status() == PaymentAttemptStatus.SUCCESS ? SUCCESS_OUTCOME : FAILURE_OUTCOME;
            meterRegistry.counter("cybertech.payments.total", "outcome", outcome).increment();
        }

        // Webhook-only side-effects: this method persists the attempt outcome and
        // returns — it does NOT publish any domain event. Stock commit / order PAID /
        // cart clear (on success) and stock release / PAYMENT_FAILED (on failure) are
        // driven EXCLUSIVELY by the Stripe webhook (PaymentWebhookServiceImp), the single
        // source of truth. Because the PaymentIntent is created with confirm=true
        // (server-side confirmation, no 3DS/redirect), Stripe emits
        // payment_intent.succeeded / payment_intent.payment_failed for every attempt, so
        // the webhook always fires. This removes the former in-process synthetic event
        // that bypassed the webhook dedup ledger and double-fired the confirmation listener.
        return paymentAttemptRepository.save(attempt);
    }

    @Transactional
    public PaymentEntity refund(OrderEntity order, PaymentType paymentType, Money amount, String idempotencyKey) {
        log.info("In refund method for order: {}, amount: {}", order.getUuid(), amount);

        final PaymentEntity paymentEntity = paymentAttemptRepository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> new PaymentNotFoundException("No payment attempt found for idempotency key: " + idempotencyKey));
        final String stripePaymentID = paymentEntity.getStripePaymentID();

        // Crée attempt de remboursement — clé déterministe basée sur la clé du paiement original
        final String refundIdempotencyKey = idempotencyKeyService.generateKey(order.getUuid().toString(), List.of("refund", idempotencyKey));
        PaymentEntity attempt = PaymentEntity.builder()
                .orderEntity(order)
                .amount(amount)
                .paymentType(paymentType)
                .transactionType(TransactionType.REFUND)
                .status(PaymentAttemptStatus.CREATED)
                .idempotencyKey(refundIdempotencyKey)
                .build();

        attempt = paymentAttemptRepository.save(attempt);
        attempt.setStatus(PaymentAttemptStatus.PROCESSING);

        log.info("Refund attempt created with ID: {}", attempt.getId());

        PaymentAttemptProcessor processor = paymentStrategyFactory.getServiceFromPaymentType(paymentType);

        log.info("Calling payment processor for refund...");
        PaymentAttemptResult result = processor.refund(order.getUuid(), amount, refundIdempotencyKey, stripePaymentID);
        log.info("Payment processor response: status={}, ref={}", result.status(), result.stripePaymentID());

        attempt.setStatus(result.status());
        attempt.setStripePaymentID(result.stripePaymentID());

        return paymentAttemptRepository.save(attempt);
    }
}