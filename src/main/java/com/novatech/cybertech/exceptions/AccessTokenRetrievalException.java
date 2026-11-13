package com.novatech.cybertech.exceptions;

public class AccessTokenRetrievalException extends RuntimeException {
    public AccessTokenRetrievalException(String message) {
        super(message);
    }

    public AccessTokenRetrievalException(String message, Throwable cause) {
        super(message, cause);
    }
}
