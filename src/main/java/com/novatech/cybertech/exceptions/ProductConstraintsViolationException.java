package com.novatech.cybertech.exceptions;

import jakarta.validation.ConstraintViolation;

import java.util.Set;

public class ProductConstraintsViolationException extends RuntimeException {
    public ProductConstraintsViolationException(String message) {
        super(message);
    }

    public ProductConstraintsViolationException(String message, Set<ConstraintViolation<Object>> violations) {
        super(message);
    }
}
