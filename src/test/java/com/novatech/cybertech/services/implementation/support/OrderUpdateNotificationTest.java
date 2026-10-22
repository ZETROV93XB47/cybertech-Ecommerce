package com.novatech.cybertech.services.implementation.support;

import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.OrderConfirmationPayload;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.entities.enums.EmailTemplateType;
import com.novatech.cybertech.entities.enums.NotificationSubject;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.annotation.NotificationTypeHandler;
import com.novatech.cybertech.services.core.NotificationProcessor;
import com.novatech.cybertech.services.implementation.OrderUpdateNotification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/** Unit tests for {@link OrderUpdateNotification} (e-mail after updateOrder / retryPayment). */
@ExtendWith(MockitoExtension.class)
class OrderUpdateNotificationTest {

    @Mock
    private NotificationProcessor notificationProcessor;

    private final OrderUpdateNotification notification = new OrderUpdateNotification();

    private static NotificationContext<OrderConfirmationPayload> context(final PaymentAttemptStatus status) {
        return NotificationContext.<OrderConfirmationPayload>builder()
                .payload(OrderConfirmationPayload.builder()
                        .orderUuid(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                        .totalAmount(new BigDecimal("249.90"))
                        .orderStatus(OrderStatus.PAID)
                        .userContactDto(UserContactDto.builder().name("Jane").email("jane@example.com").build())
                        .paymentAttemptStatus(status)
                        .build())
                .build();
    }

    @Test
    void handlesTheOrderUpdateType() {
        assertThat(OrderUpdateNotification.class.getAnnotation(NotificationTypeHandler.class).value())
                .isEqualTo(NotificationType.ORDER_UPDATE);
    }

    @Test
    void successfulUpdateUsesTheOrderUpdateTemplateAndSubject() {
        final NotificationContext<OrderConfirmationPayload> ctx = context(PaymentAttemptStatus.SUCCESS);

        notification.sendNotification(ctx, notificationProcessor);

        assertThat(ctx.getSubject()).isEqualTo(NotificationSubject.ORDER_UPDATE.getSubject());
        assertThat(ctx.getTemplatePath()).isEqualTo(EmailTemplateType.ORDER_UPDATE.getTemplatePath());
        assertThat(ctx.getTemplateVariables())
                .containsEntry("userName", "Jane")
                .containsEntry("amount", new BigDecimal("249.90"))
                .containsEntry("orderStatus", OrderStatus.PAID)
                .containsEntry("themeColor", "#27ae60");
        verify(notificationProcessor).sendMessage(ctx);
    }

    @Test
    void failedPaymentTellsTheUserToRetry() {
        final NotificationContext<OrderConfirmationPayload> ctx = context(PaymentAttemptStatus.FAILED);

        notification.sendNotification(ctx, notificationProcessor);

        assertThat(ctx.getSubject()).isEqualTo("Commande mise à jour - Paiement échoué");
        assertThat(ctx.getTemplateVariables()).containsEntry("themeColor", "#e74c3c");
        assertThat((String) ctx.getTemplateVariables().get("nextStep")).contains("relancer");
    }

    @Test
    void pendingPaymentWaitsForConfirmation() {
        final NotificationContext<OrderConfirmationPayload> ctx = context(PaymentAttemptStatus.PROCESSING);

        notification.sendNotification(ctx, notificationProcessor);

        assertThat(ctx.getSubject()).isEqualTo("Commande mise à jour - Paiement en attente");
        assertThat(ctx.getTemplateVariables()).containsEntry("themeColor", "#f39c12");
    }
}
