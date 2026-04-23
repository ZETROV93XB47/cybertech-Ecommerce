package com.novatech.cybertech.services.implementation.support;

import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.NotificationPayload;
import com.novatech.cybertech.dto.data.OrderConfirmationPayload;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.entities.enums.EmailTemplateType;
import com.novatech.cybertech.entities.enums.NotificationSubject;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.services.core.NotificationProcessor;
import com.novatech.cybertech.services.implementation.OrderConfirmationNotification;
import com.novatech.cybertech.services.implementation.ShippingConfirmationPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Unit tests for {@link OrderConfirmationNotification}.
 *
 * Pins {@code BUG-2517}: payload is cast blindly to {@link OrderConfirmationPayload}; a misrouted
 * dispatch surfaces as a raw {@link ClassCastException}.
 *
 * Also documents {@code BUG-2511}: the notification dispatch never persists a
 * {@code NotificationEntity} (no dedup, no SENT/FAILED status).
 */
@ExtendWith(MockitoExtension.class)
class OrderConfirmationNotificationTest {

    @Mock
    private NotificationProcessor notificationProcessor;

    private final OrderConfirmationNotification notification = new OrderConfirmationNotification();

    private OrderConfirmationPayload payload(final PaymentAttemptStatus status) {
        UserContactDto contact = UserContactDto.builder()
                .name("Jane Doe")
                .email("jane@example.com")
                .build();
        OrderConfirmationPayload p = new OrderConfirmationPayload();
        p.setOrderUuid(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        p.setTotalAmount(new BigDecimal("199.99"));
        p.setOrderStatus(OrderStatus.CREATED);
        p.setUserContactDto(contact);
        p.setPaymentAttemptStatus(status);
        return p;
    }

    private NotificationContext<OrderConfirmationPayload> contextWith(final OrderConfirmationPayload p) {
        NotificationContext<OrderConfirmationPayload> ctx = new NotificationContext<>();
        ctx.setPayload(p);
        ctx.setData(new HashMap<>());
        return ctx;
    }

    @Test
    @DisplayName("happy: SUCCESS payment populates green theme, ORDER_CONFIRMATION subject and template path")
    void successStatusPopulatesGreenThemeAndSubject() {
        OrderConfirmationPayload p = payload(PaymentAttemptStatus.SUCCESS);
        NotificationContext<OrderConfirmationPayload> ctx = contextWith(p);

        notification.sendNotification(ctx, notificationProcessor);

        assertThat(ctx.getSubject()).isEqualTo(NotificationSubject.ORDER_CONFIRMATION.getSubject());
        assertThat(ctx.getTemplatePath()).isEqualTo(EmailTemplateType.ORDER_CONFIRMATION.getTemplatePath());
        assertThat(ctx.getData())
                .containsEntry("userName", "Jane Doe")
                .containsEntry("orderId", p.getOrderUuid())
                .containsEntry("amount", new BigDecimal("199.99"))
                .containsEntry("email", "jane@example.com")
                .containsEntry("orderStatus", OrderStatus.CREATED)
                .containsEntry("title", "Confirmation de votre commande")
                .containsEntry("themeColor", "#27ae60")
                .containsEntry("themeBackgroundColor", "#eafaf1")
                .containsEntry("themeBorderColor", "#d5f5e3");

        verify(notificationProcessor).sendMessage(ctx);
    }

    @Test
    @DisplayName("FAILED payment renders the red theme and 'Paiement échoué' subject")
    void failedStatusPopulatesRedTheme() {
        OrderConfirmationPayload p = payload(PaymentAttemptStatus.FAILED);
        NotificationContext<OrderConfirmationPayload> ctx = contextWith(p);

        notification.sendNotification(ctx, notificationProcessor);

        assertThat(ctx.getSubject()).isEqualTo("Commande enregistrée - Paiement échoué");
        assertThat(ctx.getData())
                .containsEntry("themeColor", "#e74c3c")
                .containsEntry("themeBackgroundColor", "#fdedec")
                .containsEntry("themeBorderColor", "#fadbd8")
                .containsEntry("title", "Commande créée mais paiement non abouti");
    }

    @Test
    @DisplayName("CANCELED payment falls into the same red branch as FAILED")
    void canceledStatusUsesSameRedThemeAsFailed() {
        OrderConfirmationPayload p = payload(PaymentAttemptStatus.CANCELED);
        NotificationContext<OrderConfirmationPayload> ctx = contextWith(p);

        notification.sendNotification(ctx, notificationProcessor);

        assertThat(ctx.getSubject()).isEqualTo("Commande enregistrée - Paiement échoué");
        assertThat(ctx.getData()).containsEntry("themeColor", "#e74c3c");
    }

    @Test
    @DisplayName("PROCESSING payment falls through to orange/pending branch")
    void unknownStatusFallsToOrangeBranch() {
        OrderConfirmationPayload p = payload(PaymentAttemptStatus.PROCESSING);
        NotificationContext<OrderConfirmationPayload> ctx = contextWith(p);

        notification.sendNotification(ctx, notificationProcessor);

        assertThat(ctx.getSubject()).isEqualTo("Commande enregistrée - Paiement en attente");
        assertThat(ctx.getData())
                .containsEntry("themeColor", "#f39c12")
                .containsEntry("themeBackgroundColor", "#fef9e7")
                .containsEntry("themeBorderColor", "#fdebd0")
                .containsEntry("title", "Commande créée - Paiement en cours de validation");

        ArgumentCaptor<NotificationContext> captor = ArgumentCaptor.forClass(NotificationContext.class);
        verify(notificationProcessor).sendMessage(captor.capture());
        // BUG-2511: only the processor is called — no notification repo touch
        verifyNoMoreInteractions(notificationProcessor);
        assertThat(captor.getValue()).isSameAs(ctx);
    }

    @Test
    @DisplayName("BUG-2517: a wrong payload type triggers a raw ClassCastException (pin)")
    void wrongPayloadTypeThrowsClassCastException() {
        // ShippingConfirmationPayload is also a NotificationPayload — a misrouted strategy could pass it.
        ShippingConfirmationPayload wrong = ShippingConfirmationPayload.builder()
                .orderUuid(UUID.randomUUID())
                .userName("u")
                .build();

        NotificationContext<NotificationPayload> ctx = new NotificationContext<>();
        ctx.setPayload(wrong);
        ctx.setData(new HashMap<>());

        assertThatThrownBy(() -> notification.sendNotification(ctx, notificationProcessor))
                .isInstanceOf(ClassCastException.class);
    }
}
