package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.request.stripe.StripeWebhookEventDto;
import com.novatech.cybertech.entities.PaymentAttemptEntity;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.events.PaymentRefundedEvent;
import com.novatech.cybertech.events.PaymentSucceededEvent;
import com.novatech.cybertech.exceptions.PaymentNotFoundException;
import com.novatech.cybertech.repositories.PaymentAttemptRepository;
import com.novatech.cybertech.services.core.PaymentWebhookService;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookServiceImp implements PaymentWebhookService {

    private static final String CHARGE_REFUNDED = "charge.refunded";
    private static final String PAYMENT_INTENT_SUCCEEDED = "payment_intent.succeeded";
    private static final String PAYMENT_INTENT_PAYMENT_FAILED = "payment_intent.payment_failed";

    private final ApplicationEventPublisher eventPublisher;
    private final PaymentAttemptRepository attemptRepository;

    @Override
    @Transactional
    public void handleEvent(Event event, String eventPayload) {

        ObjectMapper mapper = new ObjectMapper();
        StripeWebhookEventDto stripeWebhookEventDto = mapper.readValue(eventPayload, StripeWebhookEventDto.class);

        switch (event.getType()) {

            case PAYMENT_INTENT_SUCCEEDED -> handlePaymentSucceeded(event, stripeWebhookEventDto);

            case PAYMENT_INTENT_PAYMENT_FAILED -> handlePaymentFailed(event, stripeWebhookEventDto);

            case CHARGE_REFUNDED -> handleRefund(event, stripeWebhookEventDto);

            default -> log.info("Unhandled Stripe event type: {}", event.getType());
        }
    }

    private void handlePaymentSucceeded(Event event, StripeWebhookEventDto stripeWebhookEventDto) {

        PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow();

        final String stripePaymentID = intent.getId();
        updatePaymentStatus(stripePaymentID, PaymentAttemptStatus.SUCCESS, event.getId());

        eventPublisher.publishEvent(new PaymentSucceededEvent(stripeWebhookEventDto));
    }

    private void handlePaymentFailed(Event event, StripeWebhookEventDto stripeWebhookEventDto) {

        PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow();

        final String stripePaymentID = intent.getId();
        updatePaymentStatus(stripePaymentID, PaymentAttemptStatus.FAILED, event.getId());
    }

    private void handleRefund(Event event, StripeWebhookEventDto stripeWebhookEventDto) {

        Charge charge = (Charge) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow();

        updatePaymentStatus(charge.getPaymentIntent(), PaymentAttemptStatus.REFUNDED, String.valueOf(stripeWebhookEventDto.getData().getPaymentIntentPayload().getId()));

        eventPublisher.publishEvent(new PaymentRefundedEvent(stripeWebhookEventDto));
    }

    private void updatePaymentStatus(String stripePaymentID, PaymentAttemptStatus status, final String stripeEventID) {

        final PaymentAttemptEntity attempt = attemptRepository
                .findByStripePaymentID(stripePaymentID)
                .orElseThrow(() -> new PaymentNotFoundException("Payment attempt not found for stripePaymentID: " + stripePaymentID));

        attempt.setStatus(status);
        attempt.setProviderEventId(stripeEventID);
        attemptRepository.save(attempt);

        log.info("Payment updated: stripePaymentID={}, status={}", stripePaymentID, status);
    }
}