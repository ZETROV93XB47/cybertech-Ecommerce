package com.novatech.cybertech.exceptions;

public class CannotCancelOrderException extends RuntimeException {
    public CannotCancelOrderException(String message) {
        super(message);
    }
}
