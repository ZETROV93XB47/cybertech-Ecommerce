package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.data.PaymentAttemptResult;
import com.novatech.cybertech.entities.valueObjects.Money;
import com.stripe.exception.StripeException;

import java.util.UUID;

public interface PaymentAttemptProcessor {
    /**
     * Charges {@code amount} with the given Stripe PaymentMethod (a Stripe test PaymentMethod chosen
     * from the user's card number, see {@code TestCardPaymentUtils}). A decline is returned as
     * {@code FAILED}; only a provider outage throws.
     */
    PaymentAttemptResult processPayment(UUID orderUuid, Money amount, String idempotencyKey, String paymentMethod);
    PaymentAttemptResult refund(UUID orderUuid, Money amount, String idempotencyKey,  String stripePaymentID);
}
