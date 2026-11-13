package com.novatech.cybertech.exceptions;

public class InvalidProductCategorySchemaException extends RuntimeException {
    public InvalidProductCategorySchemaException(String message) {
        super(message);
    }

    public InvalidProductCategorySchemaException(String message, Throwable cause) {
        super(message, cause);
    }
}
