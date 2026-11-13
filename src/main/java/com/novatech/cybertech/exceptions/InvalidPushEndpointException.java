package com.novatech.cybertech.exceptions;

public class InvalidPushEndpointException extends RuntimeException {

    private static final String DEFAULT_MESSAGE =
            "Push subscription endpoint is not an allowed HTTPS push-service URL";

    public InvalidPushEndpointException() {
        super(DEFAULT_MESSAGE);
    }

    public InvalidPushEndpointException(final String message) {
        super(message);
    }

    public InvalidPushEndpointException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
