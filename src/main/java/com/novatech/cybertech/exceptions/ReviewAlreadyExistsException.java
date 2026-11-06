package com.novatech.cybertech.exceptions;

/**
 * The user has already reviewed this product (one review per user and product). Mapped to 409
 * {@code REVIEW_ALREADY_EXISTS}; changing the review goes through {@code PUT /review/update}.
 */
public class ReviewAlreadyExistsException extends RuntimeException {
    public ReviewAlreadyExistsException(String message) {
        super(message);
    }

    public ReviewAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
