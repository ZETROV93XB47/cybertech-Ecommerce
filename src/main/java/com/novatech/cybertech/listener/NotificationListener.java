package com.novatech.cybertech.listener;

import com.novatech.cybertech.dispatcher.NotificationDispatcher;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.entities.NotificationEntity;
import com.novatech.cybertech.entities.enums.EmailTemplateType;
import com.novatech.cybertech.entities.enums.NotificationSubject;
import com.novatech.cybertech.events.OrderShippedEvent;
import com.novatech.cybertech.services.core.NotificationRetryableDelivery;
import com.novatech.cybertech.services.implementation.ShippingConfirmationPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APPLICATION_ASYNC_TASK_EXECUTOR;
import static com.novatech.cybertech.entities.enums.NotificationType.SHIPPING_CONFIRMATION;

/**
 * Sole consumer of {@link OrderShippedEvent}: builds the
 * {@link com.novatech.cybertech.entities.enums.NotificationType#SHIPPING_CONFIRMATION} context
 * and dispatches it through the {@link NotificationRetryableDelivery} for the user's preferred
 * communication channel, which in turn calls the {@link NotificationDispatcher}.
 *
 * <p>The shipping listener intentionally does NOT dispatch a notification itself — see
 * {@link ShippingListener} (BUG-122 cleanup).
 *
 * <p><b>Phase 2 hardening:</b> retries are now Resilience4j-driven. The
 * hand-rolled three-attempt {@code for} loop and the SENT/FAILED audit-row
 * branches that lived inline have been collapsed into a single
 * {@link NotificationRetryableDelivery#deliver(NotificationContext)} call. The
 * retry policy lives under the {@code notificationDispatch} instance in
 * {@code application.properties} (see {@code resilience4j.retry.instances.notificationDispatch.*}
 * — the cap is exposed via {@code cybertech.notification.dispatch.max-attempts}
 * for operator-friendly tuning).
 *
 * <p>Retry contract: dispatch is attempted up to
 * {@code cybertech.notification.dispatch.max-attempts} times with exponential
 * backoff on {@link com.novatech.cybertech.exceptions.NotificationDeliveryException}.
 * Programmer-error
 * ({@link com.novatech.cybertech.exceptions.NoStrategyFoundForProcessingTheRequest})
 * is on the {@code ignore-exceptions} allowlist and fails fast. The persisted
 * {@link NotificationEntity} reflects the final outcome — {@code SENT} on a
 * successful attempt, {@code PENDING_RETRY} when the in-process budget is
 * exhausted (Phase 3's batch tasklet then redrives the row; only that tasklet
 * may write terminal {@code FAILED}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final NotificationRetryableDelivery retryableDelivery;

    /**
     * Sends the shipping-confirmation notification once the {@link ShippingListener} has
     * committed the SHIPPED status update.
     *
     * <p>WHY {@link TransactionalEventListener} with {@link TransactionPhase#AFTER_COMMIT}: the
     * notification (email / SMS) is an external side-effect; we MUST NOT send it before the
     * SHIPPED status row is durably persisted, otherwise a transaction rollback would leave the
     * user with a confirmation for a shipment that did not happen.
     *
     * <p>Downstream effect: writes a {@link NotificationEntity} row (via the
     * retryable-delivery bean and {@link com.novatech.cybertech.services.implementation.NotificationOutcomeRecorder})
     * tracking that the confirmation was sent (recipient, channel, template,
     * sentAt) or failed after the in-process retry budget was exhausted (status
     * {@code PENDING_RETRY}, errorMessage, retryCount).
     */
    @Async(APPLICATION_ASYNC_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(final OrderShippedEvent orderShippedEvent) {
        final var dto = orderShippedEvent.getOrderEventDto();
        log.info("Received OrderShippedEvent for order {}", dto.getOrderUuid());

        final NotificationContext<ShippingConfirmationPayload> notificationContext = NotificationContext.<ShippingConfirmationPayload>builder()
                .payload(ShippingConfirmationPayload.builder()
                        .orderUuid(dto.getOrderUuid())
                        .shippingType(dto.getShippingType())
                        .shippingProvider(dto.getShippingProvider())
                        .userName(dto.getUserContactDto().getName())
                        .build())
                .subject(NotificationSubject.SHIPPING_CONFIRMATION.getSubject())
                .user(dto.getUserContactDto())
                .templatePath(EmailTemplateType.SHIPPING_CONFIRMATION.getTemplatePath())
                .notificationType(SHIPPING_CONFIRMATION)
                .communicationChanel(dto.getUserContactDto().getDefaultCommunicationChanel())
                .build();

        // Single delegate call — retry policy + audit-row persistence are
        // owned by NotificationRetryableDelivery so all three notification
        // paths (shipping confirmation here, order created/updated in
        // OrderEventListener) share an identical contract.
        retryableDelivery.deliver(notificationContext);
    }
}
