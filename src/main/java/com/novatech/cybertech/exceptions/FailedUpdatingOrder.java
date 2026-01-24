package com.novatech.cybertech.exceptions;

public class FailedUpdatingOrder extends RuntimeException {
    public FailedUpdatingOrder(String message) {
        super(message);
    }
}
