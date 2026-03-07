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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@PaymentTypeHandler({PaymentType.MASTERCARD, PaymentType.VISA})
public class StripePaymentAttemptProcessor implements PaymentAttemptProcessor {

    private static final String ORDER_UUID = "orderUuid";
    private static final String IDEMPOTENCY_KEY = "idempotencyKey";

    @Override
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

    @Override
    public PaymentAttemptResult refund(UUID orderUuid, Money amount, String idempotencyKey, String stripePaymentID) {

        log.info("idempotencykey : {}", idempotencyKey);

        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(stripePaymentID) // important
                .setAmount(amount.getAmount().toBigInteger().longValue()) //TODO: WARNING: this conversion can cause issues, refactor later
                .putMetadata("orderUuid", orderUuid.toString())
                .build();

        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        try {
            Refund refund = Refund.create(params, options);

            return new PaymentAttemptResult(
                    PaymentAttemptStatus.PROCESSING,
                    refund.getId()
            );

        } catch (StripeException e) {
            log.error("Error creating Stripe Refund for order {}: {}", orderUuid, e.getMessage());//TODO: handle specific Stripe exceptions (ex: insufficient funds, invalid payment intent, etc.) and map them to more specific error messages or codes in the PaymentAttemptResult
            throw new RuntimeException(e);
        }
    }

    private long toMinorUnit(final Money money) {

        return money.getAmount()
                .movePointRight(2) // euros → cents
                .longValueExact(); // 💥 throw si décimales incorrectes
    }

    private PaymentIntentCreateParams buildPaymentIntentParams(
            UUID orderUuid,
            Money amount,
            long amountInMinorUnit,
            String idempotencyKey
    ) {

        return PaymentIntentCreateParams.builder()
                .setAmount(amountInMinorUnit)
                .setCurrency(amount.getCurrencyCode().getCode().toLowerCase())
                .putMetadata("order_uuid", orderUuid.toString())
                .putMetadata("idempotency_key", idempotencyKey)

                // 🔥 Important
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .setAllowRedirects(
                                        PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER
                                )
                                .build()
                )

                .setConfirm(true)
                .setPaymentMethod("pm_card_visa") // DEV test
                .build();
    }

    private PaymentAttemptStatus mapStripeStatus(String stripeStatus) {

        return switch (stripeStatus) {

            case "succeeded" -> PaymentAttemptStatus.SUCCESS;

            case "requires_payment_method", "canceled" -> PaymentAttemptStatus.FAILED;

            default -> PaymentAttemptStatus.PROCESSING;
        };
    }

}
