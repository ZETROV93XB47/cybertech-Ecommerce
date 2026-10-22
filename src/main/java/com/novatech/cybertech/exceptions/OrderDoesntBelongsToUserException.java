package com.novatech.cybertech.exceptions;

public class OrderDoesntBelongsToUserException extends RuntimeException {
    public OrderDoesntBelongsToUserException(String message) {
        super(message);
    }

    public OrderDoesntBelongsToUserException(String message, Throwable cause) {
        super(message, cause);
    }
}
