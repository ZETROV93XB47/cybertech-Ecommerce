package com.novatech.cybertech.services.implementation.support;

import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.NotificationPayload;
import com.novatech.cybertech.dto.data.OrderConfirmationPayload;
import com.novatech.cybertech.entities.enums.EmailTemplateType;
import com.novatech.cybertech.entities.enums.NotificationSubject;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.services.core.NotificationProcessor;
import com.novatech.cybertech.services.implementation.ShippingConfirmationNotification;
import com.novatech.cybertech.services.implementation.ShippingConfirmationPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Unit tests for {@link ShippingConfirmationNotification}.
 *
 * Pins {@code BUG-2517}: payload is cast blindly; misroute surfaces as a {@link ClassCastException}.
 * Documents {@code BUG-2511}: no NotificationEntity persisted; no dedup repo touch.
 */
@ExtendWith(MockitoExtension.class)
class ShippingConfirmationNotificationTest {

    @Mock
    private NotificationProcessor notificationProcessor;

    private final ShippingConfirmationNotification notification = new ShippingConfirmationNotification();

    @Test
    @DisplayName("happy: populates user/order/provider/type, calls processor exactly once")
    void shouldPopulateTemplateVariablesAndDelegateToProcessor() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        ShippingConfirmationPayload payload = ShippingConfirmationPayload.builder()
                .orderUuid(orderId)
                .userName("Jane Doe")
                .shippingProvider(ShippingProvider.DHL)
                .shippingType(ShippingType.EXPRESS)
                .build();

        // subject and templatePath are set by the listener before dispatch
        NotificationContext<ShippingConfirmationPayload> ctx = NotificationContext
                .<ShippingConfirmationPayload>builder()
                .payload(payload)
                .subject(NotificationSubject.SHIPPING_CONFIRMATION.getSubject())
                .templatePath(EmailTemplateType.SHIPPING_CONFIRMATION.getTemplatePath())
                .build();

        notification.sendNotification(ctx, notificationProcessor);

        assertThat(ctx.getSubject()).isEqualTo(NotificationSubject.SHIPPING_CONFIRMATION.getSubject());
        assertThat(ctx.getTemplatePath()).isEqualTo(EmailTemplateType.SHIPPING_CONFIRMATION.getTemplatePath());
        assertThat(ctx.getTemplateVariables())
                .containsEntry("userName", "Jane Doe")
                .containsEntry("orderId", orderId)
                .containsEntry("shippingProvider", ShippingProvider.DHL)
                .containsEntry("shippingType", ShippingType.EXPRESS);

        verify(notificationProcessor).sendMessage(ctx);
        verifyNoMoreInteractions(notificationProcessor); // BUG-2511: no repo persistence
    }

    @Test
    @DisplayName("BUG-2517: passing an OrderConfirmationPayload (wrong type) raises ClassCastException")
    void wrongPayloadTypeThrowsClassCastException() {
        OrderConfirmationPayload wrong = new OrderConfirmationPayload();
        wrong.setOrderUuid(UUID.randomUUID());

        NotificationContext<NotificationPayload> ctx = NotificationContext.<NotificationPayload>builder()
                .payload(wrong)
                .build();

        assertThatThrownBy(() -> notification.sendNotification(ctx, notificationProcessor))
                .isInstanceOf(ClassCastException.class);
    }
}
