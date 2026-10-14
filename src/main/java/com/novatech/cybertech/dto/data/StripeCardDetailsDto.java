package com.novatech.cybertech.dto.data;

/**
 * Display data of a card PaymentMethod as Stripe reports it: brand, last four digits and expiry
 * only. Stripe never returns the full number or the CVC.
 *
 * @param paymentMethodId PaymentMethod id ({@code pm_...})
 * @param customerId      Customer the PaymentMethod is attached to, {@code null} if detached
 * @param brand           Stripe brand ({@code visa}, {@code mastercard}, {@code amex}...)
 * @param lastFourDigits  last four digits
 * @param expMonth        expiry month (1-12)
 * @param expYear         expiry year (four digits)
 * @param holderName      {@code billing_details.name}, {@code null} when not collected
 */
public record StripeCardDetailsDto(String paymentMethodId, String customerId, String brand, String lastFourDigits,
                                   int expMonth, int expYear, String holderName) {}
