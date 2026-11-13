package com.novatech.cybertech.exceptions;

/**
 * A notification that can never be delivered as is: the transport rejected the request itself
 * (e.g. a push service answering 400, 401, 403 or 413). Retrying the same request would fail the
 * same way.
 *
 * <p>Deliberately NOT a {@link NotificationDeliveryException}: that class is on the
 * {@code notificationDispatch} retry allowlist, and Resilience4j also retries its subclasses.
 * {@code NotificationRetryableDeliveryImp} records this failure as terminal {@code FAILED}
 * straight away, so neither the in-process retry nor the batch redrive replays it.</p>
 */
public class PermanentNotificationFailureException extends RuntimeException {

    public PermanentNotificationFailureException(final String message) {
        super(message);
    }

    public PermanentNotificationFailureException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
