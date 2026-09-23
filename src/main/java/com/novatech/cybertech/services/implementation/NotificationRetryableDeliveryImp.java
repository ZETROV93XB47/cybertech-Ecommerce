package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.entities.enums.NotificationStatus;
import com.novatech.cybertech.services.core.NotificationDispatchAttempt;
import com.novatech.cybertech.services.core.NotificationRetryableDelivery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Default {@link NotificationRetryableDelivery} implementation.
 *
 * <h2>Lifecycle</h2>
 *
 * <ol>
 *   <li>{@link #deliver(NotificationContext)} calls {@link NotificationDispatchAttempt#attemptDispatch}
 *       through the injected bean — a real inter-bean call, so Resilience4j's {@code @Retry} advice
 *       on that bean's method actually engages (see {@link NotificationDispatchAttempt} javadoc for
 *       why this had to be a separate bean rather than a method on this class).</li>
 *   <li>Resilience4j retries up to {@code max-attempts} times with exponential backoff
 *       (configured via {@code resilience4j.retry.instances.notificationDispatch.*} in
 *       {@code application.properties}) on any {@link com.novatech.cybertech.exceptions.NotificationDeliveryException}.</li>
 *   <li>If a non-retryable exception fires (e.g. the
 *       {@link com.novatech.cybertech.exceptions.NoStrategyFoundForProcessingTheRequest}
 *       under {@code ignore-exceptions}), Resilience4j short-circuits the loop and rethrows
 *       immediately instead of retrying.</li>
 *   <li>On success, control returns to {@code deliver} which records a
 *       {@link NotificationStatus#SENT} row via {@link NotificationOutcomeRecorder}.</li>
 *   <li>On exhaustion (or an ignored exception), the exception propagates out of
 *       {@code attemptDispatch} into this method's {@code catch}, which persists a
 *       {@link NotificationStatus#PENDING_RETRY} row and swallows it (the async listener call
 *       site has nowhere useful to send it further).</li>
 * </ol>
 *
 * <h2>Why {@code PENDING_RETRY} and not {@code FAILED}?</h2>
 *
 * The in-process retry policy is deliberately bounded — three attempts in a few seconds is
 * enough to ride out a momentary blip but not enough to ride out a five-minute mail-server
 * outage. {@code PENDING_RETRY} signals to the Phase 3 batch tasklet ("scan rows in this state
 * and try again later") that the row is still actionable. {@code FAILED} is the terminal
 * dead-letter state reached only after the batch tasklet has also given up. Conflating the two
 * would either trigger redrive on rows we've already abandoned or leave temporarily-failing rows
 * untouched — neither is right.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRetryableDeliveryImp implements NotificationRetryableDelivery {

    private final NotificationDispatchAttempt dispatchAttempt;
    private final NotificationOutcomeRecorder outcomeRecorder;

    /**
     * Mirrors the resilience4j {@code max-attempts} value from properties so the catch block can
     * record the correct {@code retryCount} on the audit row. Reading it from the
     * {@link io.github.resilience4j.retry.RetryRegistry} works too but is heavier; a direct
     * {@code @Value} is simpler.
     */
    @Value("${cybertech.notification.dispatch.max-attempts:3}")
    private int maxAttempts;

    /**
     * {@inheritDoc}
     *
     * <p>Implementation note on the {@code retryCount=0} we record on success: Resilience4j 2.x
     * exposes the actual attempt count via the {@code Retry} registry's event listeners, but
     * threading that through into a value available at this call site requires either a
     * {@code ThreadLocal} or registering an event consumer that mutates state — both heavier than
     * the value of the precision. The recorder field semantically means "number of failed
     * attempts before the final outcome was recorded"; on success it is at least defensible to
     * record 0 because the persisted audit row captures the *final* state, not a per-attempt
     * history. If precise attempt-count telemetry is needed later, the right place to read it is
     * Micrometer / Actuator (see {@code resilience4j-micrometer}), not this row.
     */
    @Override
    public void deliver(final NotificationContext<?> context) {
        try {
            dispatchAttempt.attemptDispatch(context);
            // Success path: a SENT row with retryCount=0 (see implementation note).
            outcomeRecorder.recordOutcome(context, NotificationStatus.SENT, 0, null);
        } catch (Throwable t) {
            // Reached once @Retry has exhausted its budget (or short-circuited on an
            // ignore-exception). Persist a PENDING_RETRY row so ops can see it and the Phase 3
            // tasklet can pick it up.
            log.warn("Notification dispatch budget exhausted for type={} channel={}: {}", context.getNotificationType(), context.getCommunicationChanel(), t.getMessage());
            outcomeRecorder.recordOutcome(context, NotificationStatus.PENDING_RETRY, maxAttempts, t);
        }
    }
}
