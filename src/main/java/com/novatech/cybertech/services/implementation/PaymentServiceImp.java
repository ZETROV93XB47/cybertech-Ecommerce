package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.PaymentAttemptResult;
import com.novatech.cybertech.dto.request.stripe.PaymentIntentPayload;
import com.novatech.cybertech.dto.request.stripe.StripeWebhookEventDto;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.entities.enums.StripeEventType;
import com.novatech.cybertech.entities.enums.TransactionType;
import com.novatech.cybertech.entities.valueObjects.Money;
import com.novatech.cybertech.events.PaymentFailedEvent;
import com.novatech.cybertech.events.PaymentSucceededEvent;
import com.novatech.cybertech.exceptions.PaymentAlreadyCompletedForThisOrderException;
import com.novatech.cybertech.exceptions.PaymentNotFoundException;
import com.novatech.cybertech.factory.PaymentStrategyFactory;
import com.novatech.cybertech.repositories.PaymentAttemptRepository;
import com.novatech.cybertech.services.core.IdempotencyKeyServiceGenerator;
import com.novatech.cybertech.services.core.PaymentAttemptProcessor;
import com.novatech.cybertech.services.core.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImp implements PaymentService {

    private final PaymentStrategyFactory paymentStrategyFactory;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final IdempotencyKeyServiceGenerator idempotencyKeyService;
    private final ApplicationEventPublisher applicationEventPublisher;

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

        final PaymentEntity persisted = paymentAttemptRepository.save(attempt);

        // BUG-070 fix: publish domain events on the direct-attempt path so downstream
        // listeners (OrderPaymentConfirmationEventListener: commit stock / mark PAID
        // on success, release stock / mark PAYMENT_FAILED on failure) fire for
        // card-present / synchronous providers that never traverse the webhook.
        // Without this the webhook path (PaymentWebhookServiceImp) was the only
        // publisher and any provider that completes inline left the order stuck in
        // AWAITING_PAYMENT with stock still reserved.
        publishPaymentOutcomeEvent(order, persisted);

        return persisted;
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

    /**
     * BUG-070 fix: build a minimal {@link StripeWebhookEventDto} carrying the
     * metadata the existing listeners require ({@code order_uuid}, plus the
     * stripe payment id and the event-type signal) and publish the matching
     * domain event on SUCCESS / FAILED. Non-terminal statuses (CREATED, PROCESSING)
     * are intentionally NOT broadcast — they are transient attempt states, not
     * business outcomes.
     */
    private void publishPaymentOutcomeEvent(final OrderEntity order, final PaymentEntity attempt) {
        final PaymentAttemptStatus status = attempt.getStatus();
        if (status != PaymentAttemptStatus.SUCCESS && status != PaymentAttemptStatus.FAILED) {
            return;
        }

        final StripeWebhookEventDto dto = buildSyntheticWebhookDto(order, attempt, status);
        if (status == PaymentAttemptStatus.SUCCESS) {
            applicationEventPublisher.publishEvent(new PaymentSucceededEvent(dto));
        } else {
            applicationEventPublisher.publishEvent(new PaymentFailedEvent(dto));
        }
    }

    private static StripeWebhookEventDto buildSyntheticWebhookDto(
            final OrderEntity order,
            final PaymentEntity attempt,
            final PaymentAttemptStatus status) {

        final PaymentIntentPayload payload = new PaymentIntentPayload();
        payload.setId(attempt.getStripePaymentID());
        payload.setObject("payment_intent");

        final Map<String, String> metadata = new HashMap<>();
        if (order != null && order.getUuid() != null) {
            metadata.put("order_uuid", order.getUuid().toString());
        }
        if (attempt.getIdempotencyKey() != null) {
            metadata.put("idempotency_key", attempt.getIdempotencyKey());
        }
        payload.setMetadata(metadata);

        final StripeWebhookEventDto.DataPayload data = new StripeWebhookEventDto.DataPayload();
        data.setPaymentIntentPayload(payload);

        final StripeWebhookEventDto dto = new StripeWebhookEventDto();
        dto.setType(status == PaymentAttemptStatus.SUCCESS
                ? StripeEventType.PAYMENT_INTENT_SUCCEEDED
                : StripeEventType.PAYMENT_INTENT_PAYMENT_FAILED);
        dto.setData(data);
        return dto;
    }

}
