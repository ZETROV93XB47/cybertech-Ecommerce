package com.novatech.cybertech.listener;

import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.OrderConfirmationPayload;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.events.OrderPaymentConfirmedEvent;
import com.novatech.cybertech.events.OrderShippedEvent;
import com.novatech.cybertech.services.core.NotificationRetryableDelivery;
import com.novatech.cybertech.services.core.WebPushDeliveryService;
import com.novatech.cybertech.services.implementation.ShippingConfirmationPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APPLICATION_ASYNC_TASK_EXECUTOR;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebPushNotificationListener {

    private static final String PAYMENT_PUSH_LOG = "Dispatching payment push notification for order {}";
    private static final String SHIPPING_PUSH_LOG = "Dispatching shipping push notification for order {}";

    private final NotificationRetryableDelivery retryableDelivery;
    private final WebPushDeliveryService webPushDeliveryService;

    @Async(APPLICATION_ASYNC_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentConfirmed(final OrderPaymentConfirmedEvent event) {
        final var order = event.getOrderEventDto();
        if (!hasSubscriptions(order.getUserContactDto())) {
            return;
        }
        log.info(PAYMENT_PUSH_LOG, order.getOrderUuid());
        final OrderConfirmationPayload payload = OrderConfirmationPayload.builder()
                .orderUuid(order.getOrderUuid())
                .totalAmount(order.getTotalAmount())
                .orderStatus(order.getOrderStatus())
                .userContactDto(order.getUserContactDto())
                .build();
        retryableDelivery.deliver(NotificationContext.<OrderConfirmationPayload>builder()
                .payload(payload)
                .user(order.getUserContactDto())
                .notificationType(NotificationType.PAYMENT_CONFIRMATION)
                .communicationChanel(CommunicationChanel.PUSH_NOTIFICATION)
                .build());
    }

    @Async(APPLICATION_ASYNC_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderShipped(final OrderShippedEvent event) {
        final var order = event.getOrderEventDto();
        if (!hasSubscriptions(order.getUserContactDto())) {
            return;
        }
        log.info(SHIPPING_PUSH_LOG, order.getOrderUuid());
        final ShippingConfirmationPayload payload = ShippingConfirmationPayload.builder()
                .orderUuid(order.getOrderUuid())
                .shippingType(order.getShippingType())
                .shippingProvider(order.getShippingProvider())
                .userName(order.getUserContactDto().getName())
                .build();
        retryableDelivery.deliver(NotificationContext.<ShippingConfirmationPayload>builder()
                .payload(payload)
                .user(order.getUserContactDto())
                .notificationType(NotificationType.SHIPPING_CONFIRMATION)
                .communicationChanel(CommunicationChanel.PUSH_NOTIFICATION)
                .build());
    }

    private boolean hasSubscriptions(final UserContactDto userContact) {
        return userContact != null
                && userContact.getKeycloakId() != null
                && !userContact.getKeycloakId().isBlank()
                && webPushDeliveryService.hasSubscriptions(userContact.getKeycloakId());
    }
}
