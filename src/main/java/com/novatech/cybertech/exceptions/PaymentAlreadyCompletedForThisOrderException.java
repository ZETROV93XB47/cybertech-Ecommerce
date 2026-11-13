package com.novatech.cybertech.exceptions;

public class PaymentAlreadyCompletedForThisOrderException extends RuntimeException {
    public PaymentAlreadyCompletedForThisOrderException(String message) {
        super(message);
    }

    public PaymentAlreadyCompletedForThisOrderException(String message, Throwable cause) {
        super(message, cause);
    }
}
