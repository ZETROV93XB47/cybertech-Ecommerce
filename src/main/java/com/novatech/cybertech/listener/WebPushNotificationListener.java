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

import java.util.List;
import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APPLICATION_ASYNC_TASK_EXECUTOR;

/**
 * Web Push is not a user-selectable channel: it is sent in addition to the user's e-mail/SMS
 * channel to every browser the user subscribed. One notification is delivered per browser, so
 * the retry/audit/redrive path handles each browser independently.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebPushNotificationListener {

    private static final String PAYMENT_PUSH_LOG = "Dispatching payment push notification for order {} to {} browser(s)";
    private static final String SHIPPING_PUSH_LOG = "Dispatching shipping push notification for order {} to {} browser(s)";

    private final NotificationRetryableDelivery retryableDelivery;
    private final WebPushDeliveryService webPushDeliveryService;

    @Async(APPLICATION_ASYNC_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentConfirmed(final OrderPaymentConfirmedEvent event) {
        final var order = event.getOrderEventDto();
        final List<UUID> subscriptions = activeSubscriptions(order.getUserContactDto());
        if (subscriptions.isEmpty()) {
            return;
        }
        log.info(PAYMENT_PUSH_LOG, order.getOrderUuid(), subscriptions.size());
        final OrderConfirmationPayload payload = OrderConfirmationPayload.builder()
                .orderUuid(order.getOrderUuid())
                .totalAmount(order.getTotalAmount())
                .orderStatus(order.getOrderStatus())
                .userContactDto(order.getUserContactDto())
                .build();
        subscriptions.forEach(subscriptionUuid -> retryableDelivery.deliver(
                NotificationContext.<OrderConfirmationPayload>builder()
                        .payload(payload)
                        .user(order.getUserContactDto())
                        .notificationType(NotificationType.PAYMENT_CONFIRMATION)
                        .communicationChanel(CommunicationChanel.PUSH_NOTIFICATION)
                        .pushSubscriptionUuid(subscriptionUuid)
                        .build()));
    }

    @Async(APPLICATION_ASYNC_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderShipped(final OrderShippedEvent event) {
        final var order = event.getOrderEventDto();
        final List<UUID> subscriptions = activeSubscriptions(order.getUserContactDto());
        if (subscriptions.isEmpty()) {
            return;
        }
        log.info(SHIPPING_PUSH_LOG, order.getOrderUuid(), subscriptions.size());
        final ShippingConfirmationPayload payload = ShippingConfirmationPayload.builder()
                .orderUuid(order.getOrderUuid())
                .shippingType(order.getShippingType())
                .shippingProvider(order.getShippingProvider())
                .userName(order.getUserContactDto().getName())
                .build();
        subscriptions.forEach(subscriptionUuid -> retryableDelivery.deliver(
                NotificationContext.<ShippingConfirmationPayload>builder()
                        .payload(payload)
                        .user(order.getUserContactDto())
                        .notificationType(NotificationType.SHIPPING_CONFIRMATION)
                        .communicationChanel(CommunicationChanel.PUSH_NOTIFICATION)
                        .pushSubscriptionUuid(subscriptionUuid)
                        .build()));
    }

    private List<UUID> activeSubscriptions(final UserContactDto userContact) {
        if (userContact == null || userContact.getKeycloakId() == null || userContact.getKeycloakId().isBlank()) {
            return List.of();
        }
        return webPushDeliveryService.findActiveSubscriptionUuids(userContact.getKeycloakId());
    }
}
