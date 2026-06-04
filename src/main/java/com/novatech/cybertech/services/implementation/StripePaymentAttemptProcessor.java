package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.annotation.PaymentTypeHandler;
import com.novatech.cybertech.dto.data.PaymentAttemptResult;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.entities.valueObjects.Money;
import com.novatech.cybertech.exceptions.PaymentProcessingException;
import com.novatech.cybertech.services.core.PaymentAttemptProcessor;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@PaymentTypeHandler({PaymentType.MASTERCARD, PaymentType.VISA})
public class StripePaymentAttemptProcessor implements PaymentAttemptProcessor {

    private static final String ORDER_UUID = "orderUuid";
    private static final String IDEMPOTENCY_KEY = "idempotencyKey";
    private static final String STRIPE_API_RESILIENCE_INSTANCE = "stripeApi";

    @Value("${stripe.payment-method:}")
    private String defaultPaymentMethod;

    // FIX(RESILIENCE): protect Stripe API calls against transient network failures — idempotency key already set on RequestOptions makes retries safe
    @Override
    @Retry(name = STRIPE_API_RESILIENCE_INSTANCE)
    @CircuitBreaker(name = STRIPE_API_RESILIENCE_INSTANCE, fallbackMethod = "processPaymentFallback")
    public PaymentAttemptResult processPayment(
            final UUID orderUuid,
            final Money amount,
            final String idempotencyKey
    ) {

        final long amountInMinorUnit = toMinorUnit(amount);

        final PaymentIntentCreateParams params = buildPaymentIntentParams(
                orderUuid,
                amount,
                amountInMinorUnit,
                idempotencyKey
        );

        final RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        try {
            log.info("Creating Stripe PaymentIntent | order={} | amount={} {} | idemKey={}",
                    orderUuid,
                    amount.getAmount(),
                    amount.getCurrencyCode(),
                    idempotencyKey
            );

            final PaymentIntent intent = PaymentIntent.create(params, options);

            final PaymentAttemptStatus status = mapStripeStatus(intent.getStatus());

            log.info("Stripe PaymentIntent created | order={} | intentId={} | stripeStatus={}",
                    orderUuid,
                    intent.getId(),
                    intent.getStatus()
            );

            return new PaymentAttemptResult(
                    status,
                    intent.getId()
            );

        } catch (StripeException e) {

            log.error("Stripe error | order={} | code={} | message={}",
                    orderUuid,
                    e.getCode(),
                    e.getMessage()
            );

            throw new PaymentProcessingException(
                    "Stripe payment failed for order " + orderUuid, e
            );
        }
    }

    // FIX(RESILIENCE): protect Stripe API calls against transient network failures — idempotency key already set on RequestOptions makes retries safe
    @Override
    @Retry(name = STRIPE_API_RESILIENCE_INSTANCE)
    @CircuitBreaker(name = STRIPE_API_RESILIENCE_INSTANCE, fallbackMethod = "refundFallback")
    public PaymentAttemptResult refund(UUID orderUuid, Money amount, String idempotencyKey, String stripePaymentID) {

        log.info("idempotencykey : {}", idempotencyKey);

        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(stripePaymentID)
                .setAmount(toMinorUnit(amount))
                .putMetadata("orderUuid", orderUuid.toString())
                .build();

        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        try {
            Refund refund = Refund.create(params, options);

            return new PaymentAttemptResult(
                    mapRefundStatus(refund.getStatus()),
                    refund.getId()
            );

        } catch (StripeException e) {
            log.error("Error creating Stripe Refund for order {}: {}", orderUuid, e.getMessage());
            throw new PaymentProcessingException("Stripe refund failed for order " + orderUuid + ": " + e.getMessage(), e);
        }
    }

    private long toMinorUnit(final Money money) {

        return money.getAmount()
                .movePointRight(2)
                .longValueExact();
    }

    private PaymentIntentCreateParams buildPaymentIntentParams(
            UUID orderUuid,
            Money amount,
            long amountInMinorUnit,
            String idempotencyKey
    ) {

        final PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                .setAmount(amountInMinorUnit)
                .setCurrency(amount.getCurrencyCode().getCode().toLowerCase())
                .putMetadata("order_uuid", orderUuid.toString())
                .putMetadata("idempotency_key", idempotencyKey)
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .setAllowRedirects(
                                        PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER
                                )
                                .build()
                )
                .setConfirm(true);

        if (defaultPaymentMethod != null && !defaultPaymentMethod.isBlank()) {
            builder.setPaymentMethod(defaultPaymentMethod);
        }

        return builder.build();
    }

    private PaymentAttemptStatus
    mapStripeStatus(String stripeStatus) {

        return switch (stripeStatus) {
            case "succeeded" -> PaymentAttemptStatus.SUCCESS;
            case "requires_payment_method", "canceled" -> PaymentAttemptStatus.FAILED;
            default -> PaymentAttemptStatus.PROCESSING;
        };
    }

    private PaymentAttemptStatus mapRefundStatus(String refundStatus) {
        return switch (refundStatus) {
            case "succeeded" -> PaymentAttemptStatus.SUCCESS;
            case "failed" -> PaymentAttemptStatus.FAILED;
            case "canceled" -> PaymentAttemptStatus.CANCELED;
            default -> PaymentAttemptStatus.PROCESSING;
        };
    }

    /**
     * Resilience4j fallback for {@link #processPayment(UUID, Money, String)}. Invoked when the
     * circuit breaker is OPEN or after the retry budget is exhausted. The signature must mirror
     * the protected method's args plus a trailing {@link Throwable}.
     */
    @SuppressWarnings("unused") // referenced by name from @CircuitBreaker(fallbackMethod = ...)
    private PaymentAttemptResult processPaymentFallback(
            final UUID orderUuid,
            final Money amount,
            final String idempotencyKey,
            final Throwable cause
    ) {
        log.error("Stripe processPayment fallback engaged | order={} | cause={}",
                orderUuid, cause != null ? cause.getMessage() : "<no cause>");
        throw new PaymentProcessingException(
                "Stripe payment unavailable for order " + orderUuid + " (resilience4j fallback)", cause);
    }

    /**
     * Resilience4j fallback for {@link #refund(UUID, Money, String, String)}. Invoked when the
     * circuit breaker is OPEN or after the retry budget is exhausted.
     */
    @SuppressWarnings("unused") // referenced by name from @CircuitBreaker(fallbackMethod = ...)
    private PaymentAttemptResult refundFallback(
            final UUID orderUuid,
            final Money amount,
            final String idempotencyKey,
            final String stripePaymentID,
            final Throwable cause
    ) {
        log.error("Stripe refund fallback engaged | order={} | cause={}",
                orderUuid, cause != null ? cause.getMessage() : "<no cause>");
        throw new PaymentProcessingException(
                "Stripe refund unavailable for order " + orderUuid + " (resilience4j fallback)", cause);
    }

}
