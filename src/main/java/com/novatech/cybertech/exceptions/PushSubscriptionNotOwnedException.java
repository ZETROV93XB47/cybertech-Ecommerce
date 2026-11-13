package com.novatech.cybertech.exceptions;

public class PushSubscriptionNotOwnedException extends RuntimeException {

    private static final String DEFAULT_MESSAGE = "Push subscription does not belong to the authenticated user";

    public PushSubscriptionNotOwnedException() {
        super(DEFAULT_MESSAGE);
    }

    public PushSubscriptionNotOwnedException(final String message) {
        super(message);
    }

    public PushSubscriptionNotOwnedException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
