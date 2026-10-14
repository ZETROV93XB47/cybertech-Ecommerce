package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.data.PaymentAttemptResult;
import com.novatech.cybertech.dto.data.PaymentSource;
import com.novatech.cybertech.entities.valueObjects.Money;
import com.stripe.exception.StripeException;

import java.util.UUID;

public interface PaymentAttemptProcessor {
    /**
     * Charges the amount on the user's saved card ({@code source}), off-session, without redirect.
     * A decline is returned as {@code FAILED}; only a provider outage throws.
     */
    PaymentAttemptResult processPayment(UUID orderUuid, Money amount, String idempotencyKey, PaymentSource source);
    PaymentAttemptResult refund(UUID orderUuid, Money amount, String idempotencyKey,  String stripePaymentID);
}
