package com.novatech.cybertech.listener;

import com.novatech.cybertech.dto.data.OrderEventDto;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.events.OrderPaymentConfirmedEvent;
import com.novatech.cybertech.events.OrderShippedEvent;
import com.novatech.cybertech.services.core.NotificationRetryableDelivery;
import com.novatech.cybertech.services.core.WebPushDeliveryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebPushNotificationListenerTest {

    private static final UUID ORDER_UUID = UUID.randomUUID();
    private static final String KEYCLOAK_ID = "kc-user";

    @Mock
    private NotificationRetryableDelivery retryableDelivery;

    @Mock
    private WebPushDeliveryService webPushDeliveryService;

    @InjectMocks
    private WebPushNotificationListener listener;

    @Test
    void paymentConfirmedSendsPushOnPushChannel() {
        when(webPushDeliveryService.hasSubscriptions(KEYCLOAK_ID)).thenReturn(true);
        listener.onPaymentConfirmed(new OrderPaymentConfirmedEvent(OrderEventDto.builder()
                .orderUuid(ORDER_UUID)
                .totalAmount(BigDecimal.TEN)
                .orderStatus(OrderStatus.PAID)
                .userContactDto(userContact())
                .build()));

        final ArgumentCaptor<com.novatech.cybertech.dto.data.NotificationContext> captor =
                ArgumentCaptor.forClass(com.novatech.cybertech.dto.data.NotificationContext.class);
        verify(retryableDelivery).deliver(captor.capture());
        assertThat(captor.getValue().getNotificationType()).isEqualTo(NotificationType.PAYMENT_CONFIRMATION);
        assertThat(captor.getValue().getCommunicationChanel()).isEqualTo(CommunicationChanel.PUSH_NOTIFICATION);
        assertThat(captor.getValue().getUser().getKeycloakId()).isEqualTo(KEYCLOAK_ID);
    }

    @Test
    void shippedOrderSendsPushOnPushChannel() {
        when(webPushDeliveryService.hasSubscriptions(KEYCLOAK_ID)).thenReturn(true);
        listener.onOrderShipped(new OrderShippedEvent(OrderEventDto.builder()
                .orderUuid(ORDER_UUID)
                .orderStatus(OrderStatus.SHIPPED)
                .shippingType(ShippingType.STANDARD)
                .shippingProvider(ShippingProvider.DHL)
                .userContactDto(userContact())
                .build()));

        final ArgumentCaptor<com.novatech.cybertech.dto.data.NotificationContext> captor =
                ArgumentCaptor.forClass(com.novatech.cybertech.dto.data.NotificationContext.class);
        verify(retryableDelivery).deliver(captor.capture());
        assertThat(captor.getValue().getNotificationType()).isEqualTo(NotificationType.SHIPPING_CONFIRMATION);
        assertThat(captor.getValue().getCommunicationChanel()).isEqualTo(CommunicationChanel.PUSH_NOTIFICATION);
    }

    @Test
    void bothEventsAreDeliveredAfterCommit() throws NoSuchMethodException {
        assertAfterCommit("onPaymentConfirmed", OrderPaymentConfirmedEvent.class);
        assertAfterCommit("onOrderShipped", OrderShippedEvent.class);
    }

    private static void assertAfterCommit(final String methodName, final Class<?> eventType) throws NoSuchMethodException {
        final var method = WebPushNotificationListener.class.getMethod(methodName, eventType);
        assertThat(method.getAnnotation(TransactionalEventListener.class).phase())
                .isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    private static UserContactDto userContact() {
        return UserContactDto.builder()
                .keycloakId(KEYCLOAK_ID)
                .email("customer@example.com")
                .build();
    }
}
