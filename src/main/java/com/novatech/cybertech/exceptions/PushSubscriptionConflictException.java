package com.novatech.cybertech.exceptions;

public class PushSubscriptionConflictException extends RuntimeException {

    private static final String DEFAULT_MESSAGE =
            "Push subscription endpoint is already associated with another user";

    public PushSubscriptionConflictException() {
        super(DEFAULT_MESSAGE);
    }

    public PushSubscriptionConflictException(final String message) {
        super(message);
    }

    public PushSubscriptionConflictException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
