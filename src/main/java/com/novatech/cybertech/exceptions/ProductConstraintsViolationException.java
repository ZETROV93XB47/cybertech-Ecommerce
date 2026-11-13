package com.novatech.cybertech.exceptions;

import com.networknt.schema.ValidationMessage;
import lombok.Getter;

import java.util.Set;

@Getter
public class ProductConstraintsViolationException extends RuntimeException {

    private final Set<ValidationMessage> violations;

    public ProductConstraintsViolationException(String message) {
        super(message);
        this.violations = Set.of();
    }

    public ProductConstraintsViolationException(String message, Set<ValidationMessage> violations) {
        super(message);
        this.violations = violations;
    }
}
