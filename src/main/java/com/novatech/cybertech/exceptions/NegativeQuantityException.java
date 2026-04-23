package com.novatech.cybertech.exceptions;

/**
 * BUG-039 — Thrown by {@code CartServiceImp.addItemsToCart} when an incoming
 * cart item carries a quantity strictly less than 1. Prevents corrupted
 * totals when an internal caller bypasses the DTO's {@code @Min(1)}
 * constraint. The {@link com.novatech.cybertech.api.error.ErrorManagementController}
 * is responsible for mapping this to a BAD_REQUEST response.
 */
public class NegativeQuantityException extends RuntimeException {
    public NegativeQuantityException(final String message) {
        super(message);
    }

    public NegativeQuantityException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
