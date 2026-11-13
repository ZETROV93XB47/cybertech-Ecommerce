package com.novatech.cybertech.exceptions;

import lombok.Getter;

/**
 * A client posted, on consume-event(s), an event type that only the backend may produce
 * (PURCHASE, ADD_TO_CART, ...). {@code field} is the request field in error, reported in
 * {@code fieldErrors} ({@code eventType}, or {@code events[i].eventType} for a batch).
 */
@Getter
public class UserEventTypeNotAcceptedException extends RuntimeException {

    private static final String DEFAULT_FIELD = "eventType";

    private final String field;

    public UserEventTypeNotAcceptedException(final String message) {
        this(DEFAULT_FIELD, message);
    }

    public UserEventTypeNotAcceptedException(final String message, final Throwable cause) {
        super(message, cause);
        this.field = DEFAULT_FIELD;
    }

    public UserEventTypeNotAcceptedException(final String field, final String message) {
        super(message);
        this.field = field;
    }
}
