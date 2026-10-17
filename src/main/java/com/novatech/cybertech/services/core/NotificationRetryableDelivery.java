package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.entities.NotificationEntity;
import com.novatech.cybertech.exceptions.NotificationDeliveryException;

/**
 * Retry-aware façade in front of
 * {@link com.novatech.cybertech.dispatcher.NotificationDispatcher} that owns the
 * Resilience4j {@code @Retry} contract and the audit-row persistence side-effect.
 *
 * <p><b>Why a separate bean (and not a method on the listener)?</b>
 * Spring AOP applies its proxy interception only when a method is invoked
 * <em>through the bean</em> — i.e. via the registered Spring bean reference.
 * A self-invocation (one method on a class calling another method on
 * {@code this}) bypasses the proxy entirely, so a {@code @Retry} annotation on
 * a method called from the same class would be silently ignored. By exposing
 * the retryable operation through this dedicated component, the three async
 * listeners ({@link com.novatech.cybertech.listener.NotificationListener},
 * {@link com.novatech.cybertech.listener.OrderEventListener}'s two handlers)
 * always cross the proxy boundary and the retry policy is honoured.
 *
 * <p><b>Phase 2 contract:</b> the implementation wraps a single dispatcher call
 * with the {@code notificationDispatch} retry instance configured in
 * {@code application.properties}. Transient failures expressed as
 * {@link NotificationDeliveryException} are retried with exponential backoff;
 * programmer-error exceptions
 * ({@link com.novatech.cybertech.exceptions.NoStrategyFoundForProcessingTheRequest})
 * are listed under {@code ignore-exceptions} and surface immediately without
 * burning retry budget.
 *
 * <p><b>Phase 3 hand-off:</b> when the in-process retry budget is exhausted
 * the row is persisted with status
 * {@link com.novatech.cybertech.entities.enums.NotificationStatus#PENDING_RETRY}
 * (NOT terminal {@code FAILED}). The Phase 3 batch tasklet picks up
 * {@code PENDING_RETRY} rows, rebuilds the context from
 * {@link com.novatech.cybertech.entities.NotificationEntity#getPayload()} and
 * resubmits through this same bean. {@code FAILED} is reserved for the moment
 * the batch tasklet itself gives up.
 */
public interface NotificationRetryableDelivery {

    /**
     * Attempts to deliver a notification, automatically retrying transient
     * failures up to {@code resilience4j.retry.instances.notificationDispatch.max-attempts}
     * times with exponential backoff. Always persists an audit row via
     * {@link com.novatech.cybertech.services.implementation.NotificationOutcomeRecorder}
     * regardless of outcome.
     *
     * <p>Returns silently on success. Does NOT propagate exceptions back to
     * the listener — async listeners have nowhere useful to surface them and
     * the audit row is the durable record of the failure.
     *
     * @param context the dispatch context (type, channel, payload, user); must not be null
     */
    void deliver(NotificationContext<?> context);

    /**
     * Redrive variant of {@link #deliver}, used exclusively by the Phase 3 batch tasklet
     * ({@code RedeliverFailedNotificationsTasklet}). Attempts the same in-process
     * Resilience4j-protected dispatch, but updates {@code existing} in place (via
     * {@link com.novatech.cybertech.services.implementation.NotificationOutcomeRecorder#updateOutcome})
     * instead of inserting a new audit row.
     *
     * <p>Exactly one {@link NotificationEntity} row tracks a given notification for its whole
     * lifecycle this way — a sustained outage bumps that single row's {@code retryCount} tick
     * after tick instead of forking an independent row that would itself become eligible for its
     * own redrive lineage (which could cause duplicate sends once the outage clears).
     *
     * @param context  the dispatch context rebuilt from {@code existing}'s persisted payload
     * @param existing the audit row to update in place; must not be null
     */
    void redeliver(NotificationContext<?> context, NotificationEntity existing);
}
