package com.novatech.cybertech.listener;

import com.novatech.cybertech.dispatcher.NotificationDispatcher;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.entities.NotificationEntity;
import com.novatech.cybertech.entities.enums.EmailTemplateType;
import com.novatech.cybertech.entities.enums.NotificationStatus;
import com.novatech.cybertech.entities.enums.NotificationSubject;
import com.novatech.cybertech.events.OrderShippedEvent;
import com.novatech.cybertech.repositories.NotificationRepository;
import com.novatech.cybertech.services.implementation.ShippingConfirmationPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APPLICATION_ASYNC_TASK_EXECUTOR;
import static com.novatech.cybertech.entities.enums.NotificationType.SHIPPING_CONFIRMATION;

/**
 * Sole consumer of {@link OrderShippedEvent}: builds the
 * {@link com.novatech.cybertech.entities.enums.NotificationType#SHIPPING_CONFIRMATION} context
 * and dispatches it through the {@link NotificationDispatcher} for the user's preferred
 * communication channel, then persists the resulting {@link NotificationEntity}.
 *
 * <p>The shipping listener intentionally does NOT dispatch a notification itself — see
 * {@link ShippingListener} (BUG-122 cleanup).
 *
 * <p>Retry contract: dispatch is attempted up to {@link #MAX_DISPATCH_RETRIES} times.
 * The persisted {@link NotificationEntity} reflects the final outcome — {@code SENT} on any
 * successful attempt, {@code FAILED} (with {@code errorMessage} + {@code retryCount}) only
 * once all retries are exhausted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    static final int MAX_DISPATCH_RETRIES = 3;

    private final NotificationRepository notificationRepository;
    private final NotificationDispatcher notificationDispatcher;

    /**
     * Sends the shipping-confirmation notification once the {@link ShippingListener} has
     * committed the SHIPPED status update.
     *
     * <p>WHY {@link TransactionalEventListener} with {@link TransactionPhase#AFTER_COMMIT}: the
     * notification (email / SMS) is an external side-effect; we MUST NOT send it before the
     * SHIPPED status row is durably persisted, otherwise a transaction rollback would leave the
     * user with a confirmation for a shipment that did not happen.
     *
     * <p>Downstream effect: writes a {@link NotificationEntity} row tracking that the
     * confirmation was sent (recipient, channel, template, sentAt) or failed after all retries
     * (status FAILED, errorMessage, retryCount).
     */
    @Async(APPLICATION_ASYNC_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(final OrderShippedEvent orderShippedEvent) {
        final var dto = orderShippedEvent.getOrderEventDto();
        log.info("Received OrderShippedEvent for order {}", dto.getOrderUuid());

        final NotificationContext notificationContext = NotificationContext.builder()
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

        Exception lastException = null;
        int attempts = 0;
        for (int attempt = 1; attempt <= MAX_DISPATCH_RETRIES; attempt++) {
            try {
                notificationDispatcher.dispatch(notificationContext);
                attempts = attempt;
                lastException = null;
                break;
            } catch (Exception ex) {
                lastException = ex;
                attempts = attempt;
                log.warn("Notification dispatch attempt {}/{} failed for order {}: {}",
                        attempt, MAX_DISPATCH_RETRIES, dto.getOrderUuid(), ex.getMessage());
            }
        }

        final NotificationEntity notificationEntity;
        if (lastException == null) {
            notificationEntity = NotificationEntity.builder()
                    .orderUuid(dto.getOrderUuid())
                    .notificationType(SHIPPING_CONFIRMATION)
                    .communicationChannel(dto.getUserContactDto().getDefaultCommunicationChanel())
                    .status(NotificationStatus.SENT)
                    .recipient(dto.getUserContactDto().getEmail())
                    .retryCount(attempts - 1)
                    .lastAttemptAt(LocalDateTime.now())
                    .sentAt(LocalDateTime.now())
                    .build();
            log.info("Shipping notification sent for order {} after {} attempt(s)", dto.getOrderUuid(), attempts);
        } else {
            notificationEntity = NotificationEntity.builder()
                    .orderUuid(dto.getOrderUuid())
                    .notificationType(SHIPPING_CONFIRMATION)
                    .communicationChannel(dto.getUserContactDto().getDefaultCommunicationChanel())
                    .status(NotificationStatus.FAILED)
                    .recipient(dto.getUserContactDto().getEmail())
                    .retryCount(attempts)
                    .lastAttemptAt(LocalDateTime.now())
                    .sentAt(null)
                    .errorMessage(lastException.getMessage())
                    .build();
            log.error("All {} dispatch attempts failed for order {}; persisting FAILED status",
                    MAX_DISPATCH_RETRIES, dto.getOrderUuid(), lastException);
        }
        notificationRepository.save(notificationEntity);
    }
}
