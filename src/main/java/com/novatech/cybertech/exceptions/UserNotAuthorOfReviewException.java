package com.novatech.cybertech.exceptions;

public class UserNotAuthorOfReviewException extends RuntimeException {
    public UserNotAuthorOfReviewException(String message) {
        super(message);
    }
}
