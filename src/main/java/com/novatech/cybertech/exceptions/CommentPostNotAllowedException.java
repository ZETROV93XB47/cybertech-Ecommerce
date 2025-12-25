package com.novatech.cybertech.exceptions;

public class CommentPostNotAllowedException extends RuntimeException {
    public CommentPostNotAllowedException(String message) {
        super(message);
    }
}
