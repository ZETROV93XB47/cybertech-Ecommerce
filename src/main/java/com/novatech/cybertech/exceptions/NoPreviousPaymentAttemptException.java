package com.novatech.cybertech.exceptions;

public class NoPreviousPaymentAttemptException extends RuntimeException {
    public NoPreviousPaymentAttemptException(String message) {
        super(message);
    }

    public NoPreviousPaymentAttemptException(String message, Throwable cause) {
        super(message, cause);
    }
}