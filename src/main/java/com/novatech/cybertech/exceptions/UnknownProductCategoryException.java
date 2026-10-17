package com.novatech.cybertech.exceptions;

public class UnknownProductCategoryException extends RuntimeException {
    public UnknownProductCategoryException(String message) {
        super(message);
    }

    public UnknownProductCategoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
