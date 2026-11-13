package com.novatech.cybertech.exceptions;

public class OrderRefundFailedException extends RuntimeException {
    public OrderRefundFailedException(String message) {
        super(message);
    }

    public OrderRefundFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
