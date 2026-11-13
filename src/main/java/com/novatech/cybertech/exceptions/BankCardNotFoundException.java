package com.novatech.cybertech.exceptions;

public class BankCardNotFoundException extends RuntimeException {
    public BankCardNotFoundException(String message) {
        super(message);
    }

    public BankCardNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
