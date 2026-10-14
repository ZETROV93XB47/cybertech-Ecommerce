package com.novatech.cybertech.exceptions;

/** A Stripe setting the requested operation needs is missing (e.g. {@code stripe.publishable-key}). */
public class PaymentProviderNotConfiguredException extends RuntimeException {

    public PaymentProviderNotConfiguredException(final String message) {
        super(message);
    }

    public PaymentProviderNotConfiguredException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
