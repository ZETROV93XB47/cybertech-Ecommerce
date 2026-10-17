package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.annotation.CommunicationTypeHandler;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.OrderConfirmationPayload;
import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.exceptions.NoStrategyFoundForProcessingTheRequest;
import com.novatech.cybertech.services.core.NotificationProcessor;
import com.novatech.cybertech.services.core.WebPushDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("pushNotificationProcessor")
@CommunicationTypeHandler(CommunicationChanel.PUSH_NOTIFICATION)
@RequiredArgsConstructor
public class PushNotificationProcessor implements NotificationProcessor {

    private static final String PAYMENT_TITLE = "Paiement confirmé";
    private static final String PAYMENT_BODY = "Le paiement de votre commande a été confirmé.";
    private static final String SHIPPING_TITLE = "Commande expédiée";
    private static final String SHIPPING_BODY = "Votre commande a été remise au transporteur.";

    private final WebPushDeliveryService webPushDeliveryService;

    @Override
    public void sendMessage(final NotificationContext notificationContext) {
        final String keycloakId = notificationContext.getUser().getKeycloakId();
        if (keycloakId == null || keycloakId.isBlank()) {
            throw new IllegalStateException("Authenticated user identity is required for Web Push delivery");
        }

        final UUID subscriptionUuid = notificationContext.getPushSubscriptionUuid();
        if (subscriptionUuid == null) {
            throw new IllegalStateException("Web Push delivery targets one subscription; none was set on the context");
        }

        final UUID orderUuid = extractOrderUuid(notificationContext);
        final String title;
        final String body;
        if (notificationContext.getNotificationType() == NotificationType.PAYMENT_CONFIRMATION) {
            title = PAYMENT_TITLE;
            body = PAYMENT_BODY;
        } else if (notificationContext.getNotificationType() == NotificationType.SHIPPING_CONFIRMATION) {
            title = SHIPPING_TITLE;
            body = SHIPPING_BODY;
        } else {
            throw new NoStrategyFoundForProcessingTheRequest(
                    "Web Push is not supported for NotificationType=" + notificationContext.getNotificationType());
        }

        webPushDeliveryService.sendToSubscription(keycloakId, subscriptionUuid, title, body, orderUuid);
    }

    private static UUID extractOrderUuid(final NotificationContext<?> notificationContext) {
        if (notificationContext.getPayload() instanceof OrderConfirmationPayload payload) {
            return payload.getOrderUuid();
        }
        if (notificationContext.getPayload() instanceof ShippingConfirmationPayload payload) {
            return payload.getOrderUuid();
        }
        throw new NoStrategyFoundForProcessingTheRequest(
                "Web Push notification payload does not contain an order UUID");
    }
}
