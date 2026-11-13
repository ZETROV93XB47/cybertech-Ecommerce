package com.novatech.cybertech.exceptions;

public class DiscountTypeNotActiveException extends RuntimeException {
    public DiscountTypeNotActiveException(String message) {
        super(message);
    }

    public DiscountTypeNotActiveException(String message, Throwable cause) {
        super(message, cause);
    }
}
