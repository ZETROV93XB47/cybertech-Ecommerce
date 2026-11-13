package com.novatech.cybertech.exceptions;

/**
 * The per-user Redis cart lock could not be acquired within the wait budget: another request on
 * the same cart is in flight. Retryable — mapped to 409 {@code CONCURRENT_MODIFICATION}.
 */
public class CartLockedException extends RuntimeException {
    public CartLockedException(String message) {
        super(message);
    }

    public CartLockedException(String message, Throwable cause) {
        super(message, cause);
    }
}
