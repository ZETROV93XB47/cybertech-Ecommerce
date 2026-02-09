package com.novatech.cybertech.exceptions;

public class OrderAlreadyShippedException extends RuntimeException {
    public OrderAlreadyShippedException(String message) {
        super(message);
    }
}
