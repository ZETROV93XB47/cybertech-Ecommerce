package com.novatech.cybertech.exceptions;

/**
 * The user already has a bank card (one card per user). Mapped to 409
 * {@code BANK_CARD_ALREADY_EXISTS}; replacing the card goes through {@code PUT /bank-card/replace}.
 */
public class BankCardAlreadyExistsException extends RuntimeException {
    public BankCardAlreadyExistsException(String message) {
        super(message);
    }

    public BankCardAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
