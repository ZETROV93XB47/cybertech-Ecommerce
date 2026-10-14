package com.novatech.cybertech.dto.data;

/**
 * What the backend needs from a Stripe SetupIntent.
 *
 * @param id              SetupIntent id ({@code seti_...})
 * @param clientSecret    secret handed to Stripe Elements to confirm the SetupIntent in the browser
 * @param customerId      Customer the SetupIntent was created for
 * @param status          Stripe status ({@code succeeded} once the card is saved)
 * @param paymentMethodId PaymentMethod saved by the SetupIntent, {@code null} until it succeeds
 */
public record StripeSetupIntentDto(String id, String clientSecret, String customerId, String status, String paymentMethodId) {}
