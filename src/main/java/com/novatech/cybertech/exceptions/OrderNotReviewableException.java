package com.novatech.cybertech.exceptions;

public class OrderNotReviewableException extends RuntimeException {
    public OrderNotReviewableException(String message) {
        super(message);
    }

    public OrderNotReviewableException(String message, Throwable cause) {
        super(message, cause);
    }
}
