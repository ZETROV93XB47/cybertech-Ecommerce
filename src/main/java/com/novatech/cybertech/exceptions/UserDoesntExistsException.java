package com.novatech.cybertech.exceptions;

public class UserDoesntExistsException extends RuntimeException {
    public UserDoesntExistsException(String message) {
        super(message);
    }
}
