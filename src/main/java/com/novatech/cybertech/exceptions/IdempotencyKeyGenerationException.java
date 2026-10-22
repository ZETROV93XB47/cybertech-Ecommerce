package com.novatech.cybertech.exceptions;

import java.security.NoSuchAlgorithmException;

public class IdempotencyKeyGenerationException extends RuntimeException {
    public IdempotencyKeyGenerationException(String message, NoSuchAlgorithmException e) {
        super(message);
    }
}
