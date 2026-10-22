package com.novatech.cybertech.exceptions;

public class CartItemNotFoundException extends RuntimeException {
    public CartItemNotFoundException(String message) {
        super(message);
    }

    public CartItemNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
