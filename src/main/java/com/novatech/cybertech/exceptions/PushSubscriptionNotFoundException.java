package com.novatech.cybertech.exceptions;

public class PushSubscriptionNotFoundException extends RuntimeException {

    private static final String DEFAULT_MESSAGE = "Push subscription not found";

    public PushSubscriptionNotFoundException() {
        super(DEFAULT_MESSAGE);
    }

    public PushSubscriptionNotFoundException(final String message) {
        super(message);
    }

    public PushSubscriptionNotFoundException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
