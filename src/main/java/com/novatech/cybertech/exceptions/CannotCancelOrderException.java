package com.novatech.cybertech.exceptions;

public class CannotCancelOrderException extends RuntimeException {
    public CannotCancelOrderException(String message) {
        super(message);
    }

    public CannotCancelOrderException(String message, Throwable cause) {
        super(message, cause);
    }
}
