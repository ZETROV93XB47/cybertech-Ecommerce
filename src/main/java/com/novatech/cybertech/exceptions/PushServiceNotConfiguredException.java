package com.novatech.cybertech.exceptions;

public class PushServiceNotConfiguredException extends RuntimeException {

    private static final String DEFAULT_MESSAGE = "Web Push is not configured";

    public PushServiceNotConfiguredException() {
        super(DEFAULT_MESSAGE);
    }

    public PushServiceNotConfiguredException(final String message) {
        super(message);
    }

    public PushServiceNotConfiguredException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
