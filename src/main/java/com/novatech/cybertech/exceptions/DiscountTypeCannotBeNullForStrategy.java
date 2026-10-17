package com.novatech.cybertech.exceptions;

public class DiscountTypeCannotBeNullForStrategy extends RuntimeException {
    public DiscountTypeCannotBeNullForStrategy(String message) {
        super(message);
    }

    public DiscountTypeCannotBeNullForStrategy(String message, Throwable cause) {
        super(message, cause);
    }
}
