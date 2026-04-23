package com.novatech.cybertech.exceptions;

/**
 * BUG-161 — Thrown when an authenticated user attempts to access or mutate a
 * cart they do not own (e.g. calling {@code GET /cart/get/{cartUuid}},
 * {@code PATCH /cart/update/{cartUuid}} or {@code DELETE /cart/delete/{cartUuid}}
 * with another user's {@code cartUuid}).
 * <p>
 * Mirrors {@link UnauthorizedBankCardAccessException}. Mapped by
 * {@code ErrorManagementController} to HTTP 403 with a FUNCTIONAL error code.
 */
public class UnauthorizedCartAccessException extends RuntimeException {
    public UnauthorizedCartAccessException(final String message) {
        super(message);
    }

    public UnauthorizedCartAccessException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
