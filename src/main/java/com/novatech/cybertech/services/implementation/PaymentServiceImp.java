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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImp implements PaymentService {

    /** Idempotency-key context token marking a refund — see {@link #refund}. */
    private static final String REFUND_ACTION = "refund";

    private final PaymentStrategyFactory paymentStrategyFactory;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final IdempotencyKeyServiceGenerator idempotencyKeyService;

    @Transactional
    public PaymentEntity processPayment(OrderEntity order, PaymentType paymentType, Money amount, String idempotencyKey) {

        // Idempotence (retry même requête)
        final Optional<PaymentEntity> existing = paymentAttemptRepository.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            final PaymentAttemptStatus existingStatus = existing.get().getStatus();
            if (existingStatus == PaymentAttemptStatus.SUCCESS || existingStatus == PaymentAttemptStatus.PROCESSING) {
                log.info("Payment already completed or in flight for this order");
                throw new PaymentAlreadyCompletedForThisOrderException("Payment already completed or in flight for this order");
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

        try {
            attempt = paymentAttemptRepository.save(attempt);
        } catch (DataIntegrityViolationException e) {
            // Race-safe second line of defence: the check above is a plain SELECT with no lock, so
            // two concurrent callers with the same idempotencyKey (double submit, retried request)
            // can both pass it before either insert commits. The uk_payment_idempotency DB
            // constraint is what actually prevents the duplicate row in that case — surface it as
            // the same domain exception the pre-check throws instead of leaking a raw 500.
            log.info("Concurrent duplicate payment attempt for idempotencyKey={} — caught by the DB constraint", idempotencyKey);
            throw new PaymentAlreadyCompletedForThisOrderException("Payment already completed or in flight for this order");
        }

        // Appel provider via Strategy
        attempt.setStatus(PaymentAttemptStatus.PROCESSING);

        PaymentAttemptProcessor processor = paymentStrategyFactory.getServiceFromPaymentType(paymentType);

        // Idée: ton processor retourne un résultat (au lieu de muter une entity PaymentEntity)
        PaymentAttemptResult result = processor.processPayment(order.getUuid(), amount, idempotencyKey);

        attempt.setStatus(result.status());
        attempt.setStripePaymentID(result.stripePaymentID());
        log.info("saved Payment id : {}", result.stripePaymentID());

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

        // Crée attempt de remboursement — clé déterministe basée sur la clé du paiement original ET
        // sur le nombre de remboursements déjà enregistrés contre ce paiement. A payment can be
        // refunded several times (updateOrder partial refund, then cancelOrder for the rest): with
        // the original key alone, the second refund reproduced the first one's key and was
        // rejected as a duplicate (409), and Stripe would have refused the reused key anyway
        // (different amount). The counter keeps the key deterministic for a genuine repeat of the
        // SAME refund (double submit / concurrent cancel both count N → same key → deduped below).
        final long priorRefunds = paymentAttemptRepository.countByOriginalPayment(paymentEntity);
        final String refundIdempotencyKey = idempotencyKeyService.generateKey(order.getUuid().toString(),
                List.of(REFUND_ACTION, idempotencyKey, String.valueOf(priorRefunds)));

        // Idempotence: block a repeat of the same refund before creating a duplicate attempt row —
        // mirrors the equivalent guard in processPayment().
        final Optional<PaymentEntity> existingRefund = paymentAttemptRepository.findByIdempotencyKey(refundIdempotencyKey);
        if (existingRefund.isPresent()) {
            final PaymentAttemptStatus existingStatus = existingRefund.get().getStatus();
            if (existingStatus == PaymentAttemptStatus.SUCCESS || existingStatus == PaymentAttemptStatus.PROCESSING) {
                log.info("Refund already completed or in flight for this payment");
                throw new PaymentAlreadyCompletedForThisOrderException("Refund already completed or in flight for this payment");
            }
        }

        PaymentEntity attempt = PaymentEntity.builder()
                .orderEntity(order)
                .amount(amount)
                .paymentType(paymentType)
                .transactionType(TransactionType.REFUND)
                .status(PaymentAttemptStatus.CREATED)
                .idempotencyKey(refundIdempotencyKey)
                // Links this refund back to the payment it targets, so a caller (cancelOrder) can
                // later compute how much of THAT SPECIFIC payment is still refundable instead of
                // assuming its full original amount always is.
                .originalPayment(paymentEntity)
                .build();

        try {
            attempt = paymentAttemptRepository.save(attempt);
        } catch (DataIntegrityViolationException e) {
            // Race-safe second line of defence — see processPayment() for the full rationale.
            log.info("Concurrent duplicate refund attempt for idempotencyKey={} — caught by the DB constraint", refundIdempotencyKey);
            throw new PaymentAlreadyCompletedForThisOrderException("Refund already completed or in flight for this payment");
        }
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