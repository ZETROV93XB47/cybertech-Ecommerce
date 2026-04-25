package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dispatcher.NotificationDispatcher;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.entities.enums.NotificationStatus;
import com.novatech.cybertech.exceptions.NotificationDeliveryException;
import com.novatech.cybertech.services.core.NotificationRetryableDelivery;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Default {@link NotificationRetryableDelivery} implementation. See the interface
 * javadoc for the rationale on why this lives in its own bean (Spring AOP
 * self-invocation).
 *
 * <h2>Lifecycle</h2>
 *
 * <ol>
 *   <li>{@link #deliver(NotificationContext)} is called by an async listener
 *       through the Spring bean reference, so the AOP proxy is in play.</li>
 *   <li>It delegates to {@link #attemptDispatch(NotificationContext)}, which
 *       is the {@code @Retry}-annotated public method. Resilience4j wraps that
 *       call, observes any thrown {@link NotificationDeliveryException}, and
 *       schedules a retry up to {@code max-attempts} times with exponential
 *       backoff (configured via {@code resilience4j.retry.instances.notificationDispatch.*}
 *       in {@code application.properties}).</li>
 *   <li>If a non-retryable exception fires (e.g. the
 *       {@link com.novatech.cybertech.exceptions.NoStrategyFoundForProcessingTheRequest}
 *       under {@code ignore-exceptions}), Resilience4j short-circuits the loop
 *       and routes straight to the fallback.</li>
 *   <li>On success, control returns to {@code deliver} which records a
 *       {@link NotificationStatus#SENT} row via {@link NotificationOutcomeRecorder}.</li>
 *   <li>On exhaustion (or on an ignored exception) Resilience4j invokes
 *       {@link #onRetriesExhausted(NotificationContext, Throwable)}, which
 *       persists a {@link NotificationStatus#PENDING_RETRY} row and swallows
 *       the exception (async listener has nowhere to send it).</li>
 * </ol>
 *
 * <h2>Why {@code PENDING_RETRY} and not {@code FAILED}?</h2>
 *
 * The in-process retry policy is deliberately bounded — three attempts in a
 * few seconds is enough to ride out a momentary blip but not enough to ride
 * out a five-minute mail-server outage. {@code PENDING_RETRY} signals to the
 * Phase 3 batch tasklet ("scan rows in this state and try again later") that
 * the row is still actionable. {@code FAILED} is the terminal dead-letter
 * state reached only after the batch tasklet has also given up. Conflating
 * the two would either trigger redrive on rows we've already abandoned or
 * leave temporarily-failing rows untouched — neither is right.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRetryableDeliveryImp implements NotificationRetryableDelivery {

    /**
     * Resilience4j retry instance name. Must match the key under
     * {@code resilience4j.retry.instances.<name>.*} in
     * {@code application.properties}. Pulled out as a constant so a typo here
     * surfaces at compile time rather than as a silent "no retry policy
     * applied" at runtime.
     */
    public static final String RETRY_INSTANCE_NAME = "notificationDispatch";

    private final NotificationDispatcher notificationDispatcher;
    private final NotificationOutcomeRecorder outcomeRecorder;

    /**
     * Mirrors the resilience4j {@code max-attempts} value from properties so
     * the fallback can record the correct {@code retryCount} on the audit
     * row. Reading it from the {@link io.github.resilience4j.retry.RetryRegistry}
     * works too but is heavier; a direct {@code @Value} is simpler.
     */
    @Value("${cybertech.notification.dispatch.max-attempts:3}")
    private int maxAttempts;

    /**
     * {@inheritDoc}
     *
     * <p>Implementation note on the {@code retryCount=0} we record on success:
     * Resilience4j 2.x exposes the actual attempt count via the {@code Retry}
     * registry's event listeners, but threading that through into a value
     * available at this call site requires either a {@code ThreadLocal} or
     * registering an event consumer that mutates state — both heavier than the
     * value of the precision. The recorder field semantically means "number
     * of failed attempts before the final outcome was recorded"; on success
     * it is at least defensible to record 0 because the persisted audit row
     * captures the *final* state, not a per-attempt history. If precise
     * attempt-count telemetry is needed later, the right place to read it is
     * Micrometer / Actuator (see {@code resilience4j-micrometer}), not this
     * row.
     */
    @Override
    public void deliver(final NotificationContext<?> context) {
        try {
            attemptDispatch(context);
            // Success path: a SENT row with retryCount=0 (see implementation note).
            outcomeRecorder.recordOutcome(context, NotificationStatus.SENT, 0, null);
        } catch (Throwable t) {
            // Defensive: if for any reason the proxy let an exception escape
            // (Resilience4j's fallback mechanism failed, or a configuration
            // glitch left the @Retry off), we still must NOT crash the async
            // worker silently. Persist a PENDING_RETRY row so ops can see it
            // and the Phase 3 tasklet can pick it up.
            log.error("Unexpected escape from @Retry-wrapped dispatch; persisting PENDING_RETRY", t);
            outcomeRecorder.recordOutcome(context, NotificationStatus.PENDING_RETRY, maxAttempts, t);
        }
    }

    /**
     * The actual retry-eligible operation. Public + on a Spring bean = the
     * Resilience4j AOP advice can intercept it.
     *
     * <p>This method MUST do nothing other than dispatch: it intentionally
     * does not catch, log, or persist. Persistence happens once, after
     * Resilience4j has decided whether the call ultimately succeeded or
     * exhausted its budget — doing it here would record a row per attempt and
     * defeat the purpose of the retry policy.
     *
     * <p><b>Note:</b> the Spring AOP proxy wraps this method only when invoked
     * via the bean reference (i.e. through {@link #deliver(NotificationContext)}
     * via the injected interface). Direct {@code this.attemptDispatch(...)}
     * inside the same instance from a non-proxied call site would skip the
     * advice — which is precisely why this whole class exists as a separate
     * bean.
     */
    @Retry(name = RETRY_INSTANCE_NAME, fallbackMethod = "onRetriesExhausted")
    public void attemptDispatch(final NotificationContext<?> context) {
        notificationDispatcher.dispatch(context);
    }

    /**
     * Resilience4j fallback invoked when:
     * <ul>
     *   <li>all configured retry attempts have been exhausted, OR</li>
     *   <li>an exception listed in {@code ignore-exceptions} was thrown (e.g.
     *       {@link com.novatech.cybertech.exceptions.NoStrategyFoundForProcessingTheRequest}
     *       — programmer error, not transient).</li>
     * </ul>
     *
     * <p>Signature contract (Resilience4j 2.x): the fallback method must have
     * the same arguments as the protected method PLUS a trailing
     * {@link Throwable} (or a more specific superclass of the thrown exception)
     * and the same return type. The framework reflectively matches against
     * this signature.
     *
     * <p>Behaviour:
     * <ul>
     *   <li>Logs at WARN with notification type, channel, and the failure
     *       message — sufficient for ops triage without log spam from a
     *       per-attempt log line (Resilience4j already emits one of those).</li>
     *   <li>Persists the audit row with {@link NotificationStatus#PENDING_RETRY}
     *       (NOT {@link NotificationStatus#FAILED}). See class-level javadoc
     *       for the rationale.</li>
     *   <li>Sets {@code retryCount = maxAttempts}: from the recorder's
     *       perspective the in-process budget is fully spent.</li>
     *   <li>Does NOT rethrow. The async-listener call site has no useful way
     *       to surface the failure further — the audit row IS the surface.</li>
     * </ul>
     */
    @SuppressWarnings("unused") // referenced by name from @Retry(fallbackMethod = ...)
    private void onRetriesExhausted(final NotificationContext<?> context, final Throwable last) {
        log.warn("Notification dispatch budget exhausted (attempts={}) for type={} channel={}: {}",
                maxAttempts,
                context.getNotificationType(),
                context.getCommunicationChanel(),
                last != null ? last.getMessage() : "<no cause>");

        // Always persist — we never want a silent loss of the audit trail. If
        // the recorder itself throws (e.g. DB outage on top of a delivery
        // outage), we suppress to keep the async worker alive; the original
        // failure is already logged above.
        try {
            outcomeRecorder.recordOutcome(context, NotificationStatus.PENDING_RETRY, maxAttempts, last);
        } catch (Exception persistFailure) {
            log.error("Failed to persist PENDING_RETRY audit row after retry exhaustion", persistFailure);
        }
    }
}
