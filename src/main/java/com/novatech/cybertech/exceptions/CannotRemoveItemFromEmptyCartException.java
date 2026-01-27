package com.novatech.cybertech.exceptions;

public class CannotRemoveItemFromEmptyCartException extends RuntimeException {
    public CannotRemoveItemFromEmptyCartException(String message) {
        super(message);
    }
}
