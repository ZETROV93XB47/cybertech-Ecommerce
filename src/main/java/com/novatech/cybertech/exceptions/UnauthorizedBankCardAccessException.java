package com.novatech.cybertech.exceptions;

/**
 * Thrown when an authenticated user attempts to access or mutate a bank card
 * they do not own (e.g. calling {@code deleteByUUID(cardUuid, keycloakId)} on a
 * card belonging to another user). Modelled on
 * {@link UnauthorizedCartAccessException}.
 */
public class UnauthorizedBankCardAccessException extends RuntimeException {
    public UnauthorizedBankCardAccessException(final String message) {
        super(message);
    }

    public UnauthorizedBankCardAccessException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
