package com.novatech.cybertech.services.implementation.support;

import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.NotificationPayload;
import com.novatech.cybertech.dto.data.OrderConfirmationPayload;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.entities.enums.EmailTemplateType;
import com.novatech.cybertech.entities.enums.NotificationSubject;
import com.novatech.cybertech.services.core.NotificationProcessor;
import com.novatech.cybertech.services.implementation.PaymentConfirmationNotification;
import com.novatech.cybertech.services.implementation.ShippingConfirmationPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Unit tests for {@link PaymentConfirmationNotification}.
 */
@ExtendWith(MockitoExtension.class)
class PaymentConfirmationNotificationTest {

    @Mock
    private NotificationProcessor notificationProcessor;

    private final PaymentConfirmationNotification notification = new PaymentConfirmationNotification();

    @Test
    @DisplayName("happy: populates user/order/amount, keeps subject and template, calls processor exactly once")
    void shouldPopulateTemplateVariablesAndDelegateToProcessor() {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
        OrderConfirmationPayload payload = OrderConfirmationPayload.builder()
                .orderUuid(orderId)
                .totalAmount(new BigDecimal("249.99"))
                .userContactDto(UserContactDto.builder().name("Jane Doe").email("jane@example.com").build())
                .build();

        // subject and templatePath are set by the listener before dispatch
        NotificationContext<OrderConfirmationPayload> ctx = NotificationContext
                .<OrderConfirmationPayload>builder()
                .payload(payload)
                .subject(NotificationSubject.PAYMENT_CONFIRMATION.getSubject())
                .templatePath(EmailTemplateType.PAYMENT_CONFIRMATION.getTemplatePath())
                .build();

        notification.sendNotification(ctx, notificationProcessor);

        assertThat(ctx.getSubject()).isEqualTo(NotificationSubject.PAYMENT_CONFIRMATION.getSubject());
        assertThat(ctx.getTemplatePath()).isEqualTo(EmailTemplateType.PAYMENT_CONFIRMATION.getTemplatePath());
        assertThat(ctx.getTemplateVariables())
                .containsEntry("userName", "Jane Doe")
                .containsEntry("orderId", orderId)
                .containsEntry("amount", new BigDecimal("249.99"));

        verify(notificationProcessor).sendMessage(ctx);
        verifyNoMoreInteractions(notificationProcessor);
    }

    @Test
    @DisplayName("passing a ShippingConfirmationPayload (wrong type) raises ClassCastException")
    void wrongPayloadTypeThrowsClassCastException() {
        NotificationContext<NotificationPayload> ctx = NotificationContext.<NotificationPayload>builder()
                .payload(ShippingConfirmationPayload.builder().orderUuid(UUID.randomUUID()).build())
                .build();

        assertThatThrownBy(() -> notification.sendNotification(ctx, notificationProcessor))
                .isInstanceOf(ClassCastException.class);
    }
}
