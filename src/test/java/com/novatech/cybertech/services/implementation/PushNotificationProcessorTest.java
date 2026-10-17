package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.OrderConfirmationPayload;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.exceptions.NoStrategyFoundForProcessingTheRequest;
import com.novatech.cybertech.services.core.WebPushDeliveryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PushNotificationProcessorTest {

    private static final String USER_ID = "kc-user";
    private static final UUID ORDER_UUID = UUID.randomUUID();
    private static final UUID SUBSCRIPTION_UUID = UUID.randomUUID();

    @Mock
    private WebPushDeliveryService deliveryService;

    @InjectMocks
    private PushNotificationProcessor processor;

    @Test
    void sendsGenericPaymentMessageToAuthenticatedUser() {
        processor.sendMessage(NotificationContext.<OrderConfirmationPayload>builder()
                .user(UserContactDto.builder().keycloakId(USER_ID).build())
                .notificationType(NotificationType.PAYMENT_CONFIRMATION)
                .pushSubscriptionUuid(SUBSCRIPTION_UUID)
                .payload(OrderConfirmationPayload.builder().orderUuid(ORDER_UUID).build())
                .build());

        verify(deliveryService).sendToSubscription(
                USER_ID,
                SUBSCRIPTION_UUID,
                "Paiement confirmé",
                "Le paiement de votre commande a été confirmé.",
                ORDER_UUID);
    }

    @Test
    void rejectsUnsupportedNotificationType() {
        assertThatThrownBy(() -> processor.sendMessage(NotificationContext.builder()
                .user(UserContactDto.builder().keycloakId(USER_ID).build())
                .notificationType(NotificationType.ORDER_CONFIRMATION)
                .pushSubscriptionUuid(SUBSCRIPTION_UUID)
                .payload(OrderConfirmationPayload.builder().orderUuid(ORDER_UUID).build())
                .build()))
                .isInstanceOf(NoStrategyFoundForProcessingTheRequest.class);
    }

    @Test
    void rejectsContextWithoutTargetedSubscription() {
        assertThatThrownBy(() -> processor.sendMessage(NotificationContext.<OrderConfirmationPayload>builder()
                .user(UserContactDto.builder().keycloakId(USER_ID).build())
                .notificationType(NotificationType.PAYMENT_CONFIRMATION)
                .payload(OrderConfirmationPayload.builder().orderUuid(ORDER_UUID).build())
                .build()))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(deliveryService);
    }
}
