package com.novatech.cybertech.exceptions;

/**
 * Signals that a user-facing notification failed to be delivered through its
 * underlying transport (mail server, SMS gateway, ...).
 *
 * <p><b>Why unchecked?</b> The notification dispatch path runs inside Spring's
 * {@code @Async} executor on a {@link org.springframework.transaction.event.TransactionalEventListener}
 * after a domain commit. Checked exceptions there cannot be propagated up to a
 * caller that could meaningfully react to them — the originating transaction
 * has already committed and the user request has long since returned. Treating
 * delivery failures as runtime exceptions lets the dispatch chain rethrow
 * without polluting every signature, while still being a precise type that
 * upstream retry/recovery code can pattern-match on.
 *
 * <p><b>Phase 2 contract:</b> this is the canonical retry-eligible signal.
 * The Resilience4j {@code @Retry} configured on the notification dispatcher
 * will register this class on its {@code retry-exceptions} allowlist so that
 * transient SMTP / template-rendering glitches are retried with backoff while
 * non-recoverable failures (a {@link NullPointerException} from a coding bug,
 * for example) bubble out without burning retry budget. <em>Do not</em> catch
 * unrelated runtime exceptions and rewrap them as
 * {@code NotificationDeliveryException} — that would silently extend the retry
 * surface and hide programming errors.
 *
 * <p><b>Phase 3 contract:</b> a {@link com.novatech.cybertech.entities.NotificationEntity}
 * row whose terminal failure was a {@code NotificationDeliveryException} is the
 * candidate set redriven by the upcoming batch tasklet.
 */
public class NotificationDeliveryException extends RuntimeException {

    public NotificationDeliveryException(final String message) {
        super(message);
    }

    public NotificationDeliveryException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
