package com.novatech.cybertech.listener;

import com.novatech.cybertech.dispatcher.NotificationDispatcher;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.entities.enums.NotificationStatus;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.events.OrderCreatedEvent;
import com.novatech.cybertech.events.OrderUpdatedEvent;
import com.novatech.cybertech.services.implementation.NotificationOutcomeRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APPLICATION_ASYNC_TASK_EXECUTOR;

/**
 * Listens for order lifecycle events ({@link OrderCreatedEvent}, {@link OrderUpdatedEvent}) and
 * dispatches the matching notification through the {@link NotificationDispatcher}.
 *
 * <p>Both handlers run {@link Async} on the application task executor so the notification I/O
 * does not block the transaction commit thread.
 *
 * <p><b>Phase 1 hardening:</b> previously these two handlers fired
 * {@link NotificationDispatcher#dispatch} fire-and-forget — no retries, no
 * audit row, no trace in the database when delivery failed. They now persist
 * a {@link com.novatech.cybertech.entities.NotificationEntity} via the shared
 * {@link NotificationOutcomeRecorder} on every dispatch (success or failure)
 * so the SHIPPING_CONFIRMATION and ORDER_*-paths share a uniform audit
 * surface. <em>No retry is added here</em> — that's Phase 2's job; for now
 * each dispatch is a single attempt recorded with {@code retryCount=0} on
 * success or {@code retryCount=1} on failure.
 *
 * <p>Async-listener exception contract: dispatch failures are logged but
 * <em>never</em> propagated. We're already on a worker thread after the
 * domain commit; throwing here would only kill the worker without any caller
 * able to react.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final NotificationDispatcher notificationDispatcher;
    private final NotificationOutcomeRecorder outcomeRecorder;

    /**
     * Sends an {@link NotificationType#ORDER_CONFIRMATION} notification when an order is
     * created.
     *
     * <p>WHY {@link TransactionalEventListener} with {@link TransactionPhase#AFTER_COMMIT}: the
     * confirmation MUST only be sent once the order's row is durably persisted. A pre-commit
     * fire-and-forget would risk telling the user about an order that ultimately rolled back.
     */
    @Async(APPLICATION_ASYNC_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(final OrderCreatedEvent event) {

        log.info("In order created event listener");

        final Map<String, Object> orderEventDto = new HashMap<>();
        orderEventDto.put("orderEventDto", event.getOrderEventDto());

        final NotificationContext context = NotificationContext.builder()
                .notificationType(NotificationType.ORDER_CONFIRMATION)
                .user(event.getOrderEventDto().getUserContactDto())
                .data(orderEventDto)
                .build();

        dispatchAndRecord(context);
    }

    /**
     * Sends an {@link NotificationType#ORDER_UPDATE} notification on order mutation
     * (e.g. quantity changes, status transitions surfaced as updates).
     *
     * <p>WHY {@link TransactionalEventListener} with {@link TransactionPhase#AFTER_COMMIT}:
     * see {@link #onOrderCreated(OrderCreatedEvent)} — same reasoning, the user must not be
     * notified of an update that has not yet committed.
     */
    @Async(APPLICATION_ASYNC_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderUpdated(final OrderUpdatedEvent event) {

        log.info("In order updated event listener");

        final Map<String, Object> orderEventDto = new HashMap<>();
        orderEventDto.put("orderEventDto", event.getOrderEventDto());

        final NotificationContext context = NotificationContext.builder()
                .notificationType(NotificationType.ORDER_UPDATE)
                .user(event.getOrderEventDto().getUserContactDto())
                .data(orderEventDto)
                .build();

        dispatchAndRecord(context);
    }

    /**
     * Single-attempt dispatch with audit-row persistence on both branches.
     * <p>
     * Phase 2 will move retry semantics into a {@code @Retry}-annotated wrapper
     * around the dispatcher, at which point both order paths automatically
     * inherit retries without changes here.
     */
    private void dispatchAndRecord(final NotificationContext<?> context) {
        try {
            notificationDispatcher.dispatch(context);
            outcomeRecorder.recordOutcome(context, NotificationStatus.SENT, 0, null);
        } catch (Exception ex) {
            // Single-attempt path → one failed try → retryCount = 1.
            // We log + persist + swallow: this is an @Async listener after a
            // committed transaction, so propagation goes nowhere useful and
            // would only show up as "Unhandled exception in async executor"
            // in the logs without any actionable trace.
            log.error("Order-event notification dispatch failed for type={} (recipient={})",
                    context.getNotificationType(),
                    context.getUser() != null ? context.getUser().getEmail() : "<unknown>",
                    ex);
            try {
                outcomeRecorder.recordOutcome(context, NotificationStatus.FAILED, 1, ex);
            } catch (Exception persistenceFailure) {
                // Persisting the audit row should never fail in practice, but
                // if it does (e.g. DB outage during the async commit) we
                // suppress it: we already logged the original dispatch
                // failure, and there's no upstream that can do anything with
                // a second exception.
                log.error("Failed to persist FAILED audit row after dispatch error", persistenceFailure);
            }
        }
    }
}
