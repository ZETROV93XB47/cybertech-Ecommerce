package com.novatech.cybertech.dto.data;

/**
 * The saved card a payment is charged on: the user's Stripe Customer and the PaymentMethod
 * attached to it. Only Stripe identifiers, never card data.
 */
public record PaymentSource(String customerId, String paymentMethodId) {}
