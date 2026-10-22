package com.novatech.cybertech.utils;

/**
 * Decides the outcome of a payment from the card number the user saved (portfolio project: no
 * real card is ever charged).
 *
 * <p>A number made only of {@code 1} (e.g. {@code 1111 1111 1111 1111}) pays; any other number,
 * {@code 0000 0000 0000 0000} included, is declined. The outcome is played out by Stripe in test
 * mode: the PaymentIntent is confirmed with one of Stripe's test PaymentMethods, so the real
 * {@code payment_intent.succeeded} / {@code payment_intent.payment_failed} webhook drives the order
 * to PAID or PAYMENT_FAILED as usual.</p>
 *
 * <p>Card numbers and outcomes: {@code docs/test-cards.md}.</p>
 */
public final class TestCardPaymentUtils {

    /** Stripe test PaymentMethod whose payment succeeds. */
    public static final String STRIPE_SUCCEEDING_TEST_PAYMENT_METHOD = "pm_card_visa";

    /** Stripe test PaymentMethod whose payment is declined ({@code card_declined / generic_decline}). */
    public static final String STRIPE_DECLINED_TEST_PAYMENT_METHOD = "pm_card_chargeDeclined";

    private static final char PAYING_DIGIT = '1';

    private TestCardPaymentUtils() {
        // utility class — no instantiation
    }

    /** {@code true} only for a non-empty number made exclusively of the digit {@code 1}. */
    public static boolean isPayingCard(final String cardNumber) {
        return cardNumber != null
                && !cardNumber.isEmpty()
                && cardNumber.chars().allMatch(digit -> digit == PAYING_DIGIT);
    }

    /** The Stripe test PaymentMethod that reproduces the outcome of {@code cardNumber}. */
    public static String stripeTestPaymentMethodFor(final String cardNumber) {
        return isPayingCard(cardNumber) ? STRIPE_SUCCEEDING_TEST_PAYMENT_METHOD : STRIPE_DECLINED_TEST_PAYMENT_METHOD;
    }
}
