package com.novatech.cybertech.exceptions;

public class FailedRetryingPayment extends RuntimeException {
    public FailedRetryingPayment(String message) {
        super(message);
    }

    public FailedRetryingPayment(String message, Throwable cause) {
        super(message, cause);
    }
}
