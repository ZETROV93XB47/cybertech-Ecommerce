package com.novatech.cybertech.exceptions;

public class CartIsEmptyException extends RuntimeException {
    public CartIsEmptyException(String message) {
        super(message);
    }

    public CartIsEmptyException(String message, Throwable cause) {
        super(message, cause);
    }
}
