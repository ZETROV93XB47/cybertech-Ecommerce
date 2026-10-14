package com.novatech.cybertech.exceptions;

/**
 * The card setup cannot be used: unknown SetupIntent, SetupIntent not {@code succeeded} yet, or a
 * card change Stripe refused.
 */
public class InvalidCardSetupException extends RuntimeException {

    public InvalidCardSetupException(final String message) {
        super(message);
    }

    public InvalidCardSetupException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
