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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

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
     * confirmation was sent (recipient, channel, template, sentAt).
     */
    @Async(APPLICATION_ASYNC_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(final OrderShippedEvent orderShippedEvent) {
        log.info("Received OrderShippedEvent for order with context: {}", orderShippedEvent.getOrderEventDto());

        final ShippingConfirmationPayload shippingConfirmationPayload = ShippingConfirmationPayload.builder()
                .orderUuid(orderShippedEvent.getOrderEventDto().getOrderUuid())
                .shippingType(orderShippedEvent.getOrderEventDto().getShippingType())
                .shippingProvider(orderShippedEvent.getOrderEventDto().getShippingProvider())
                .userName(orderShippedEvent.getOrderEventDto().getUserContactDto().getName())
                .build();

        final NotificationContext notificationContext = NotificationContext.builder()
                .payload(shippingConfirmationPayload)
                .subject(NotificationSubject.SHIPPING_CONFIRMATION.getSubject())
                .user(orderShippedEvent.getOrderEventDto().getUserContactDto())
                .templatePath(EmailTemplateType.SHIPPING_CONFIRMATION.getTemplatePath())
                .notificationType(SHIPPING_CONFIRMATION)
                .communicationChanel(orderShippedEvent.getOrderEventDto().getUserContactDto().getDefaultCommunicationChanel())
                .build();

        notificationDispatcher.dispatch(notificationContext);


        //TODO: improve and finish this part implementing retry mechanism and error handling
        final NotificationEntity notificationEntity = NotificationEntity.builder()
                .orderUuid(orderShippedEvent.getOrderEventDto().getOrderUuid())
                .notificationType(SHIPPING_CONFIRMATION)
                .communicationChannel(orderShippedEvent.getOrderEventDto().getUserContactDto().getDefaultCommunicationChanel())
                .status(NotificationStatus.SENT)
                .recipient(orderShippedEvent.getOrderEventDto().getUserContactDto().getEmail())
                .retryCount(0)
                .lastAttemptAt(null)
                .sentAt(LocalDateTime.now())
                .build();

        notificationRepository.save(notificationEntity);
        log.info("Shipping Notification sent successfully");
    }
}
