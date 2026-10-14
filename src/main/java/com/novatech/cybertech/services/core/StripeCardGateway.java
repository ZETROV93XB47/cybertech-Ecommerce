package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.data.StripeCardDetailsDto;
import com.novatech.cybertech.dto.data.StripeSetupIntentDto;

import java.util.UUID;

/**
 * Stripe calls of the saved-card flow (Customer, SetupIntent, PaymentMethod).
 *
 * <p>Isolates the SDK's static API so the card service is unit-testable and the ITs can replace
 * Stripe with a mock. Failures are translated: a Stripe outage becomes
 * {@code PaymentProcessingException}, an id Stripe does not know or a rejected card update becomes
 * {@code InvalidCardSetupException}.</p>
 */
public interface StripeCardGateway {

    /** Creates the Customer of a user; idempotent per user, so a double click creates only one. */
    String createCustomer(UUID userUuid, String email, String name);

    /** Creates an off-session card SetupIntent for the Customer. */
    StripeSetupIntentDto createSetupIntent(String customerId);

    StripeSetupIntentDto retrieveSetupIntent(String setupIntentId);

    StripeCardDetailsDto retrievePaymentMethod(String paymentMethodId);

    void attachPaymentMethod(String paymentMethodId, String customerId);

    /** Makes the PaymentMethod the Customer's default ({@code invoice_settings.default_payment_method}). */
    void setDefaultPaymentMethod(String customerId, String paymentMethodId);

    void updatePaymentMethod(String paymentMethodId, String holderName, int expMonth, int expYear);

    void detachPaymentMethod(String paymentMethodId);
}
