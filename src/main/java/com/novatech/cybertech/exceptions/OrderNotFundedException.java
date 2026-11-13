package com.novatech.cybertech.exceptions;

public class OrderNotFundedException extends RuntimeException {
    public OrderNotFundedException(String message) {
        super(message);
    }

    public OrderNotFundedException(String message, Throwable cause) {
        super(message, cause);
    }
}
